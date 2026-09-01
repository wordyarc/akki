package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideOrSelf
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class LoggerCallLowering(
    private val context: IrPluginContext,
) : IrElementTransformerVoidWithContext() {
    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()

        val target = expression.target() ?: return expression
        val scope = currentScope?.scope?.scopeOwnerSymbol ?: return expression
        val builder = DeclarationIrBuilder(context, scope, expression.startOffset, expression.endOffset)
        return with(builder) {
            irBlock(resultType = context.irBuiltIns.unitType) {
                val receiver = irTemporary(requireNotNull(expression.arguments[0]), "logger")

                val sinkCall = irCall(target.sink.symbol).apply {
                    arguments[0] = irGet(receiver)
                    arguments[1] = IrGetEnumValueImpl(
                        startOffset,
                        endOffset,
                        target.level.parentAsClass.defaultType,
                        target.level.symbol,
                    )
                }
                val sink = irTemporary(sinkCall, "sink")

                +irIfThen(
                    irNotEquals(irGet(sink), irNull()),
                    if (target.isLazy) {
                        irBlock {
                            val cause = irTemporary(
                                expression.arguments[1] ?: irNull(target.emit.parameters[2].type),
                                "cause",
                            )
                            val fields = irTemporary(
                                expression.arguments[2] ?: emptyFields(target.emit),
                                "fields",
                            )

                            val messageArgument = requireNotNull(expression.arguments[3])
                            val message = if (messageArgument is IrFunctionExpression) {
                                +messageArgument.function
                                irCall(messageArgument.function.symbol)
                            } else {
                                val provider = irTemporary(messageArgument, "message")
                                val invoke = provider.type.classOrNull?.owner
                                    ?.functions
                                    ?.single { function ->
                                        function.name.asString() == "invoke" &&
                                            function.parameters.count { it.kind == IrParameterKind.Regular } == 0
                                    }
                                    ?: error("Cannot resolve Function0.invoke")
                                irCall(invoke.symbol, context.irBuiltIns.stringType).apply {
                                    arguments[0] = irGet(provider)
                                }
                            }

                            +irCall(target.emit.symbol).apply {
                                arguments[0] = irGet(sink)
                                arguments[1] = message
                                arguments[2] = irGet(cause)
                                arguments[3] = irGet(fields)
                            }
                        }
                    } else {
                        irCall(target.emit.symbol).apply {
                            arguments[0] = irGet(sink)
                            arguments[1] = requireNotNull(expression.arguments[1])
                            arguments[2] = expression.arguments[2] ?: irNull(target.emit.parameters[2].type)
                            arguments[3] = expression.arguments[3] ?: emptyFields(target.emit)
                        }
                    },
                )
            }
        }
    }

    private fun IrCall.target(): Target? {
        val function = symbol.owner.resolveFakeOverrideOrSelf() as? IrSimpleFunction ?: return null
        val logger = function.parent as? IrClass ?: return null
        if (logger.kotlinFqName != LOGGER_FQ_NAME) return null
        val levelName = LEVELS[function.name.asString()] ?: return null
        val regularParameters = function.parameters.filter { it.kind == IrParameterKind.Regular }
        if (regularParameters.size != 3) return null

        val sink = logger.functions.single { candidate ->
            candidate.name.asString() == "sink" &&
                candidate.parameters.count { it.kind == IrParameterKind.Regular } == 1
        }
        val sinkClass = sink.returnType.classOrNull?.owner ?: return null
        val emit = sinkClass.functions.single { candidate ->
            candidate.name.asString() == "emit" &&
                candidate.parameters.count { it.kind == IrParameterKind.Regular } == 3
        }
        val levelClass = sink.parameters.single { it.kind == IrParameterKind.Regular }.type.classOrNull?.owner
            ?: return null
        val level = levelClass.declarations
            .filterIsInstance<IrEnumEntry>()
            .single { it.name.asString() == levelName }
        return Target(
            sink = sink,
            emit = emit,
            level = level,
            isLazy = regularParameters.last().name.asString() == "message",
        )
    }

    private fun IrBuilderWithScope.emptyFields(
        emit: IrSimpleFunction,
    ): IrExpression {
        val fieldsType = emit.parameters[3].type
        val emptyMap = context.irBuiltIns.symbolFinder.findFunctions(EMPTY_MAP_CALLABLE_ID)
            .single { it.owner.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular } }
        return irCall(emptyMap, fieldsType).apply {
            typeArguments[0] = context.irBuiltIns.stringType
            typeArguments[1] = context.irBuiltIns.anyNType
        }
    }

    private data class Target(
        val sink: IrSimpleFunction,
        val emit: IrSimpleFunction,
        val level: IrEnumEntry,
        val isLazy: Boolean,
    )

    private companion object {
        val LOGGER_FQ_NAME: FqName = FqName("dev.ashenarx.akki.Logger")
        val EMPTY_MAP_CALLABLE_ID: CallableId = CallableId(
            packageName = FqName("kotlin.collections"),
            callableName = Name.identifier("emptyMap"),
        )
        val LEVELS: Map<String, String> = mapOf(
            "trace" to "TRACE",
            "debug" to "DEBUG",
            "info" to "INFO",
            "warn" to "WARN",
            "error" to "ERROR",
        )
    }
}
