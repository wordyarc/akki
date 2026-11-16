@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.akki.compiler.ir

import io.akki.compiler.AkkiErrors
import io.akki.compiler.MinLevel
import io.akki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.IrInlinable
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
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irFalse
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.isUnchanging
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class LoggerCallLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
    private val minLevel: MinLevel,
    private val compat: CompilerCompat,
) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    override fun lower(irFile: IrFile) {
        irFile.transform(this, null)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        if (symbols.isLevelCheck(expression.symbol.owner)) return foldLevelCheck(expression) ?: expression
        val target = symbols.callFor(expression.symbol.owner) ?: return expression
        return if (minLevel.clips(target.level.owner.name)) {
            clip(expression, target) ?: expression
        } else {
            lowerToEmit(expression, target) ?: expression
        }
    }

    private fun clip(call: IrCall, target: LoggerCall): IrExpression? {
        val scope = currentScope?.scope?.scopeOwnerSymbol ?: return null
        with(compat) { context.diagnosticReporter.diagnosticContext(call, currentFile) }.report(
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

    private fun foldLevelCheck(call: IrCall): IrExpression? {
        val level = (call.arguments[1] as? IrGetEnumValue)?.symbol ?: return null
        if (!minLevel.clips(level.owner.name)) return null
        val receiver = call.arguments[0] ?: return null
        val scope = currentScope?.scope?.scopeOwnerSymbol ?: return null
        return DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset)
            .irBlock(resultType = context.irBuiltIns.booleanType) {
                +receiver
                +irFalse()
            }
    }

    private fun lowerToEmit(call: IrCall, target: LoggerCall): IrExpression? {
        val receiver = call.arguments[target.receiver.indexInParameters] ?: return null
        val message = call.arguments[target.message.indexInParameters] ?: return null
        val scope = currentScope?.scope?.scopeOwnerSymbol ?: return null
        val builder = DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset)
        return builder.irBlock(resultType = context.irBuiltIns.unitType) {
            val logger = freeze(receiver, "logger")
            if (target.isLazy) lazyRecord(call, target, logger, message) else eagerRecord(call, target, logger, message)
        }
    }

    private fun IrBlockBuilder.eagerRecord(
        call: IrCall,
        target: LoggerCall,
        logger: IrExpression,
        message: IrExpression,
    ) {
        val text = freeze(message, "message")
        val cause = freeze(cause(call, target), "cause")
        val fields = freeze(fields(call, target), "fields")
        whenEnabled(logger, target) { sink -> +emit(sink, text, cause, fields) }
    }

    private fun IrBlockBuilder.lazyRecord(
        call: IrCall,
        target: LoggerCall,
        logger: IrExpression,
        message: IrExpression,
    ) {
        val cause = freeze(cause(call, target), "cause")
        val fields = freeze(fields(call, target), "fields")
        val body = message.asInlinable(this)
        whenEnabled(logger, target) { sink ->
            if (body is IrInlinableLambda && !body.readsRecord()) {
                +emit(sink, body.inline(parent), cause, fields)
            } else {
                val record = irTemporary(
                    irCallConstructor(symbols.newRecordScope, emptyList()).apply { arguments[0] = fields },
                    "record",
                )
                val written = irCall(symbols.recordFields).apply { arguments[0] = irGet(record) }
                +emit(sink, invoke(body, record), cause, written)
            }
        }
    }

    private fun IrBlockBuilder.whenEnabled(
        logger: IrExpression,
        target: LoggerCall,
        record: IrBlockBuilder.(sink: IrVariable) -> Unit,
    ) {
        val sink = irTemporary(
            irCall(symbols.sink).apply {
                arguments[0] = logger
                arguments[1] = IrGetEnumValueImpl(startOffset, endOffset, symbols.levelType, target.level)
            },
            "sink",
        )
        +irIfThen(irNotEquals(irGet(sink), irNull()), irBlock { record(sink) })
    }

    private fun IrBuilderWithScope.emit(
        sink: IrVariable,
        message: IrExpression,
        cause: IrExpression,
        fields: IrExpression,
    ): IrExpression = irCall(symbols.emit).apply {
        arguments[0] = irGet(sink)
        arguments[1] = message
        arguments[2] = cause
        arguments[3] = fields
    }

    private fun IrBuilderWithScope.invoke(body: IrInlinable, record: IrVariable): IrExpression =
        when (body) {
            is IrInlinableLambda -> body.inline(parent, listOf(record))
            is IrInvokable -> irCall(symbols.invoke, context.irBuiltIns.stringType).apply {
                arguments[0] = irGet(body.invokable)
                arguments[1] = irGet(record)
            }
        }

    private fun IrInlinableLambda.readsRecord(): Boolean {
        val record = function.parameters.getOrNull(boundArguments.size)?.symbol ?: return false
        var read = false
        function.body?.acceptChildrenVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    if (!read) element.acceptChildrenVoid(this)
                }

                override fun visitGetValue(expression: IrGetValue) {
                    if (expression.symbol == record) read = true
                }
            },
        )
        return read
    }

    private fun IrBuilderWithScope.cause(call: IrCall, target: LoggerCall): IrExpression =
        call.arguments[target.cause.indexInParameters] ?: irNull(symbols.causeType)

    private fun IrBuilderWithScope.fields(call: IrCall, target: LoggerCall): IrExpression =
        call.arguments[target.fields.indexInParameters] ?: emptyFields()

    private fun IrBlockBuilder.freeze(value: IrExpression, name: String): IrExpression =
        if (value.isUnchanging()) value else irGet(irTemporary(value, name))

    private fun IrBuilderWithScope.emptyFields(): IrExpression =
        irCall(symbols.emptyMap, symbols.fieldsType).apply {
            typeArguments[0] = context.irBuiltIns.stringType
            typeArguments[1] = context.irBuiltIns.anyNType
        }
}
