@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.akki.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.IrInlinableLambda
import org.jetbrains.kotlin.backend.common.ir.IrInvokable
import org.jetbrains.kotlin.backend.common.ir.asInlinable
import org.jetbrains.kotlin.backend.common.ir.inline
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.isUnchanging
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType

internal class LoggerCallLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
    private val minLevel: MinLevel,
) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    override fun lower(irFile: IrFile) {
        irFile.transform(this, null)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        val target = symbols.callFor(expression.symbol.owner) ?: return expression
        return if (minLevel.clips(target.level.owner.name)) {
            clip(expression, target) ?: expression
        } else {
            record(expression, target) ?: expression
        }
    }

    private fun clip(call: IrCall, target: LoggerCall): IrExpression? {
        val scope = currentScope?.scope?.scopeOwnerSymbol ?: return null
        context.diagnosticReporter.at(call, currentFile).report(
            AkkiErrors.LOGGING_CALL_REMOVED,
            target.level.owner.name.asString().lowercase(),
            minLevel.option,
        )
        val builder = DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset)
        return with(builder) {
            irBlock(resultType = context.irBuiltIns.unitType) {
                call.arguments.forEachIndexed { index, argument ->
                    if (argument == null) return@forEachIndexed
                    if (target.isLazy && index == target.message.indexInParameters) {
                        argument.asInlinable(this)
                    } else {
                        +argument
                    }
                }
                +irUnit()
            }
        }
    }

    private fun record(call: IrCall, target: LoggerCall): IrExpression? {
        val receiver = call.arguments[target.receiver.indexInParameters] ?: return null
        val message = call.arguments[target.message.indexInParameters] ?: return null
        val scope = currentScope?.scope?.scopeOwnerSymbol ?: return null
        val builder = DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset)
        return with(builder) {
            irBlock(resultType = context.irBuiltIns.unitType) {
                val selected = freeze(receiver, "logger")
                val emitted = emitArguments(call, target, message)
                val sink = irTemporary(
                    irCall(symbols.sink).apply {
                        arguments[0] = selected
                        arguments[1] = IrGetEnumValueImpl(startOffset, endOffset, symbols.levelType, target.level)
                    },
                    "sink",
                )
                +irIfThen(
                    irNotEquals(irGet(sink), irNull()),
                    irBlock {
                        +irCall(symbols.emit).apply {
                            arguments[0] = irGet(sink)
                            emitted.forEach { (slot, value) -> arguments[slot] = value }
                        }
                    },
                )
            }
        }
    }

    private fun IrBlockBuilder.emitArguments(
        call: IrCall,
        target: LoggerCall,
        message: IrExpression,
    ): List<Pair<IrValueParameter, IrExpression>> = target.arguments.map { argument ->
        val written = call.arguments[argument.source.indexInParameters]
        val value = when (argument.slot) {
            EmitSlot.MESSAGE -> if (target.isLazy) invoke(message) else message
            EmitSlot.CAUSE -> written ?: irNull(argument.destination.type)
            EmitSlot.FIELDS -> written ?: emptyFields(argument.destination.type)
        }
        argument.destination to if (argument.slot == EmitSlot.MESSAGE && target.isLazy) {
            value
        } else {
            freeze(value, argument.source.name.asString())
        }
    }

    private fun IrBlockBuilder.freeze(value: IrExpression, name: String): IrExpression =
        if (value.isUnchanging()) value else irGet(irTemporary(value, name))

    private fun IrBlockBuilder.invoke(message: IrExpression): IrExpression =
        when (val inlinable = message.asInlinable(this)) {
            is IrInlinableLambda -> inlinable.inline(parent)
            is IrInvokable -> irCall(symbols.invoke, context.irBuiltIns.stringType).apply {
                arguments[0] = irGet(inlinable.invokable)
            }
        }

    private fun IrBuilderWithScope.emptyFields(type: IrType): IrExpression =
        irCall(symbols.emptyMap, type).apply {
            typeArguments[0] = context.irBuiltIns.stringType
            typeArguments[1] = context.irBuiltIns.anyNType
        }
}
