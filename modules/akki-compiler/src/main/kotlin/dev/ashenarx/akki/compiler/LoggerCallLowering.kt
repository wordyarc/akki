@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
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
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideOrSelf
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal class LoggerCallLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
) : IrElementTransformerVoidWithContext() {
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

    private fun IrCall.target(): LoggerCall? {
        if (superQualifierSymbol != null) return null
        val function = symbol.owner.resolveFakeOverrideOrSelf() as? IrSimpleFunction ?: return null
        return symbols.callFor(function)
    }

    private fun lower(call: IrCall, target: LoggerCall, hoisted: List<IrVariable>): IrExpression? {
        val receiver = call.arguments[0] ?: return null
        val message = call.arguments[target.message] ?: return null
        val scope = currentScope?.scope?.scopeOwnerSymbol ?: return null
        val outer = hoisted.reachableFrom(receiver)
        val builder = DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset)
        return with(builder) {
            irBlock(resultType = context.irBuiltIns.unitType) {
                hoisted.forEach { if (it in outer) +it }
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
                        hoisted.forEach { if (it !in outer) +it }
                        +irCall(symbols.emit).apply {
                            arguments[0] = irGet(sink)
                            emitArguments(call, target, message).forEach { (index, value) -> arguments[index] = value }
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
    ): Map<Int, IrExpression> {
        val slots = listOf(
            target.message to symbols.emitMessage,
            target.cause to symbols.emitCause,
            target.fields to symbols.emitFields,
        ).sortedBy { (source, _) -> source }
        val emitOrder = slots.map { (_, destination) -> destination }
        val reordered = emitOrder != emitOrder.sorted()
        return slots.associate { (source, destination) ->
            val value = when (source) {
                target.message -> if (target.isLazy) invoke(message) else message
                target.cause -> call.arguments[target.cause] ?: irNull(symbols.causeType)
                else -> call.arguments[target.fields] ?: emptyFields()
            }
            destination to if (reordered) irGet(irTemporary(value, "argument")) else value
        }
    }

    private fun IrBlockBuilder.invoke(message: IrExpression): IrExpression =
        if (message is IrFunctionExpression) {
            +message.function
            irCall(message.function.symbol)
        } else {
            val provider = irTemporary(message, "message")
            irCall(symbols.invoke, context.irBuiltIns.stringType).apply { arguments[0] = irGet(provider) }
        }

    private fun IrBuilderWithScope.emptyFields(): IrExpression {
        val typeArguments = symbols.fieldsType.typeArguments().orEmpty()
        return irCall(symbols.emptyMap, symbols.fieldsType).apply {
            typeArguments.forEachIndexed { index, argument -> this.typeArguments[index] = argument }
        }
    }

    private fun List<IrVariable>.reachableFrom(expression: IrExpression): Set<IrVariable> {
        if (isEmpty()) return emptySet()
        val declared = associateBy { it.symbol }
        val reached = linkedSetOf<IrVariable>()
        val visitor = object : IrVisitorVoid() {
            override fun visitElement(element: IrElement): Unit = element.acceptChildrenVoid(this)

            override fun visitGetValue(expression: IrGetValue) {
                val variable = declared[expression.symbol] ?: return
                if (reached.add(variable)) variable.initializer?.acceptVoid(this)
            }
        }
        expression.acceptVoid(visitor)
        return reached
    }
}
