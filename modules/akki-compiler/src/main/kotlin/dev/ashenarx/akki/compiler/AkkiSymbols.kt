@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class LoggerCall(
    val level: IrEnumEntrySymbol,
    val message: Int,
    val cause: Int,
    val fields: Int,
    val isLazy: Boolean,
)

internal class AkkiSymbols private constructor(
    val logger: IrClassSymbol,
    val sink: IrSimpleFunctionSymbol,
    val emit: IrSimpleFunctionSymbol,
    val invoke: IrSimpleFunctionSymbol,
    val emptyMap: IrSimpleFunctionSymbol,
    val levelType: IrType,
    val causeType: IrType,
    val fieldsType: IrType,
    val emitMessage: Int,
    val emitCause: Int,
    val emitFields: Int,
    private val calls: Map<IrSimpleFunctionSymbol, LoggerCall>,
) {
    fun callFor(function: IrSimpleFunction): LoggerCall? {
        if (function.parentClassOrNull?.symbol != logger) return null
        return calls[function.symbol]
    }

    companion object {
        private val LOGGER_ID = ClassId(FqName("dev.ashenarx.akki"), Name.identifier("Logger"))
        private val EMPTY_MAP_ID = CallableId(FqName("kotlin.collections"), Name.identifier("emptyMap"))
        private val SINK = Name.identifier("sink")
        private val EMIT = Name.identifier("emit")
        private val INVOKE = Name.identifier("invoke")

        fun of(context: IrPluginContext): AkkiSymbols? {
            val finder = context.finderForBuiltins()
            val logger = finder.findClass(LOGGER_ID) ?: return null
            val function0 = context.irBuiltIns.functionN(0).symbol

            val sink = logger.owner.functions.singleOrNull { it.name == SINK && it.regular.size == 1 } ?: return null
            val levelClass = sink.regular.single().type.classOrNull ?: return null
            val emit = sink.returnType.classOrNull
                ?.owner
                ?.functions
                ?.singleOrNull { it.name == EMIT && it.regular.size == 3 }
                ?: return null

            val emitCause = emit.regular.singleOrNull { it.type.isNullable() } ?: return null
            val emitFields = emit.regular.singleOrNull { it.type.classOrNull == context.irBuiltIns.mapClass }
                ?: return null
            val emitMessage = emit.regular.singleOrNull { it != emitCause && it != emitFields } ?: return null
            if (emitMessage.type != context.irBuiltIns.stringType) return null
            if (emitFields.type.typeArguments()?.size != 2) return null

            val entries = levelClass.owner.declarations
                .filterIsInstance<IrEnumEntry>()
                .associateBy { it.name.asString() }
            val calls = logger.owner.functions.mapNotNull { function ->
                val level = entries[function.name.asString().uppercase()] ?: return@mapNotNull null
                val parameters = function.regular
                if (parameters.size != 3) return@mapNotNull null
                val cause = parameters.singleOrNull { it.type.classOrNull == emitCause.type.classOrNull }
                    ?: return@mapNotNull null
                val fields = parameters.singleOrNull { it.type.classOrNull == emitFields.type.classOrNull }
                    ?: return@mapNotNull null
                val message = parameters.singleOrNull { it != cause && it != fields } ?: return@mapNotNull null
                val isLazy = message.type.classOrNull == function0
                if (!isLazy && message.type != context.irBuiltIns.stringType) return@mapNotNull null
                function.symbol to LoggerCall(
                    level = level.symbol,
                    message = message.indexInParameters,
                    cause = cause.indexInParameters,
                    fields = fields.indexInParameters,
                    isLazy = isLazy,
                )
            }.toMap()
            if (calls.isEmpty()) return null

            val invoke = function0.owner.functions.singleOrNull { it.name == INVOKE } ?: return null
            val emptyMap = finder.findFunctions(EMPTY_MAP_ID).singleOrNull { it.owner.regular.isEmpty() } ?: return null

            return AkkiSymbols(
                logger = logger,
                sink = sink.symbol,
                emit = emit.symbol,
                invoke = invoke.symbol,
                emptyMap = emptyMap,
                levelType = levelClass.owner.defaultType,
                causeType = emitCause.type,
                fieldsType = emitFields.type,
                emitMessage = emitMessage.indexInParameters,
                emitCause = emitCause.indexInParameters,
                emitFields = emitFields.indexInParameters,
                calls = calls,
            )
        }
    }
}

internal val IrSimpleFunction.regular: List<IrValueParameter>
    get() = parameters.filter { it.kind == IrParameterKind.Regular }

internal fun IrType.typeArguments(): List<IrType>? {
    val arguments = (this as? IrSimpleType)?.arguments ?: return null
    return arguments.map { it.typeOrNull ?: return null }
}
