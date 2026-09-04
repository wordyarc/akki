@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.IrInlinableLambda
import org.jetbrains.kotlin.backend.common.ir.IrInvokable
import org.jetbrains.kotlin.backend.common.ir.asInlinable
import org.jetbrains.kotlin.backend.common.ir.inline
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.isUnchanging
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal class LoggerCallLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    override fun lower(irFile: IrFile) {
        irFile.transform(this, null)
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        if (expression.origin != IrStatementOrigin.ARGUMENTS_REORDERING_FOR_CALL) return super.visitBlock(expression)
        val call = expression.statements.lastOrNull() as? IrCall ?: return super.visitBlock(expression)
        val hoisted = expression.statements.dropLast(1).map { it as? IrVariable ?: return super.visitBlock(expression) }
        val target = call.target() ?: return super.visitBlock(expression)
        expression.statements.forEach { it.transformChildrenVoid() }
        return lower(call, target, hoisted) ?: expression
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        val target = expression.target() ?: return expression
        return lower(expression, target, emptyList()) ?: expression
    }

    private fun IrCall.target(): LoggerCall? = symbols.callFor(symbol.owner)

    private fun lower(call: IrCall, target: LoggerCall, hoisted: List<IrVariable>): IrExpression? {
        val receiver = call.arguments[target.receiver] ?: return null
        val message = call.arguments[target.message] ?: return null
        val scope = currentScope?.scope?.scopeOwnerSymbol ?: return null
        val beforeGuard = hoisted.prefixReadBy(receiver)
        val builder = DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset)
        return with(builder) {
            irBlock(origin = IrStatementOrigin.SAFE_CALL, resultType = context.irBuiltIns.unitType) {
                hoisted.take(beforeGuard).forEach { +it }
                val logger = irTemporary(receiver, "logger")
                val sink = irTemporary(
                    irCall(symbols.sink).apply {
                        arguments[0] = irGet(logger)
                        arguments[1] = IrGetEnumValueImpl(startOffset, endOffset, symbols.levelType, target.level)
                    },
                    "sink",
                )
                +irIfThen(
                    irNotEquals(irGet(sink), irNull()),
                    irBlock {
                        hoisted.drop(beforeGuard).forEach { +it }
                        +irCall(symbols.emit).apply {
                            arguments[0] = irGet(sink)
                            emitArguments(call, target, message).forEach { (slot, value) -> arguments[slot] = value }
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
    ): List<Pair<IrValueParameter, IrExpression>> {
        val slots = listOf(
            target.message to symbols.emitMessage,
            target.cause to symbols.emitCause,
            target.fields to symbols.emitFields,
        ).sortedBy { (source, _) -> source.indexInParameters }
        val materialisedThrough = slots.map { (_, destination) -> destination.indexInParameters }
            .zipWithNext()
            .indexOfLast { (previous, next) -> previous > next }
        return slots.mapIndexed { position, (source, destination) ->
            val written = call.arguments[source]
            val value = when (source) {
                target.message -> if (target.isLazy) invoke(message) else message
                target.cause -> written ?: irNull(symbols.causeType)
                else -> written ?: emptyFields()
            }
            val materialised = position <= materialisedThrough && written != null && !value.isUnchanging()
            destination to if (materialised) irGet(irTemporary(value, "argument")) else value
        }
    }

    private fun IrBlockBuilder.invoke(message: IrExpression): IrExpression =
        when (val inlinable = message.asInlinable(this)) {
            is IrInlinableLambda -> inlinable.inline(parent)
            is IrInvokable -> irCall(symbols.invoke, context.irBuiltIns.stringType).apply {
                arguments[0] = irGet(inlinable.invokable)
            }
        }

    private fun IrBuilderWithScope.emptyFields(): IrExpression =
        irCall(symbols.emptyMap, symbols.fieldsType).apply {
            symbols.fieldTypes.forEachIndexed { index, argument -> typeArguments[index] = argument }
        }

    private fun List<IrVariable>.prefixReadBy(expression: IrExpression): Int {
        if (isEmpty()) return 0
        val read = mutableSetOf<IrValueSymbol>()
        expression.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement): Unit = element.acceptChildrenVoid(this)

            override fun visitGetValue(expression: IrGetValue) {
                read += expression.symbol
            }
        })
        return indexOfLast { it.symbol in read } + 1
    }
}
