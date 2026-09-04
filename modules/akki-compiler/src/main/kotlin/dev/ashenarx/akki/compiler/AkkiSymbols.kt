@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.hasShape
import org.jetbrains.kotlin.ir.util.invokeFun
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal class LoggerCall(
    val level: IrEnumEntrySymbol,
    val receiver: IrValueParameter,
    val message: IrValueParameter,
    val cause: IrValueParameter,
    val fields: IrValueParameter,
    val isLazy: Boolean,
)

internal class AkkiSymbols private constructor(context: IrPluginContext, finder: DeclarationFinder) {
    val logger: IrClassSymbol = finder.classOrFail(AkkiNames.LOGGER_ID)
    val loggerType: IrType = logger.owner.defaultType
    val logRegistry: IrClassSymbol = finder.classOrFail(AkkiNames.LOG_REGISTRY_ID)

    private val level: IrClassSymbol = finder.classOrFail(AkkiNames.LEVEL_ID)
    private val sinkClass: IrClassSymbol = finder.classOrFail(AkkiNames.SINK_ID)

    val levelType: IrType = level.owner.defaultType

    val sink: IrSimpleFunctionSymbol = logger.functionOrFail(AkkiNames.SINK, SINK_SIGNATURE) {
        it.hasShape(dispatchReceiver = true, regularParameters = 1) &&
            it.returnType.classOrNull == sinkClass &&
            it.nonDispatchParameters.single().type.classOrNull == level
    }.symbol

    val forCaller: IrSimpleFunctionSymbol = logRegistry.functionOrFail(AkkiNames.FOR_CALLER, FOR_CALLER_SIGNATURE) {
        it.hasShape(dispatchReceiver = true) && it.returnType.classOrNull == logger
    }.symbol

    private val emitFunction: IrSimpleFunction = sinkClass.functionOrFail(AkkiNames.EMIT, EMIT_SIGNATURE) {
        it.hasShape(dispatchReceiver = true, regularParameters = 3)
    }

    val emit: IrSimpleFunctionSymbol = emitFunction.symbol
    val emitMessage: IrValueParameter =
        emitFunction.parameterOrFail(AkkiNames.MESSAGE, EMIT_SIGNATURE, context.irBuiltIns.stringType)
    val emitCause: IrValueParameter = emitFunction.parameterOrFail(AkkiNames.CAUSE, EMIT_SIGNATURE)
    val emitFields: IrValueParameter = emitFunction.parameterOrFail(AkkiNames.FIELDS, EMIT_SIGNATURE)

    val causeType: IrType = emitCause.type
    val fieldsType: IrType = emitFields.type
    val fieldTypes: List<IrType> = fieldsType.arguments().takeIf { it.size == 2 } ?: incompatible(EMIT_SIGNATURE)

    val emptyMap: IrSimpleFunctionSymbol = finder.findFunctions(AkkiNames.EMPTY_MAP_ID)
        .singleOrNull { it.owner.hasShape() }
        ?: incompatible("kotlin.collections.emptyMap()")

    val invoke: IrSimpleFunctionSymbol =
        (context.irBuiltIns.functionN(0).invokeFun ?: incompatible("kotlin.Function0.invoke()")).symbol

    private val calls: Map<IrSimpleFunctionSymbol, LoggerCall> = buildMap {
        val function0 = context.irBuiltIns.functionN(0).symbol
        for (entry in level.owner.declarations.filterIsInstance<IrEnumEntry>()) {
            for (overload in finder.findFunctions(AkkiNames.levelId(entry.name))) {
                val call = overload.owner.loggerCall(context, entry.symbol, function0) ?: continue
                put(overload, call)
            }
        }
    }

    fun callFor(function: IrSimpleFunction): LoggerCall? = calls[function.symbol]

    fun isCallSite(function: IrSimpleFunction): Boolean =
        function.returnType.classOrNull == logger &&
            function.nonDispatchParameters.isEmpty() &&
            (
                function.hasAnnotation(AkkiNames.CALL_SITE_ID) ||
                    function.correspondingPropertySymbol?.owner?.hasAnnotation(AkkiNames.CALL_SITE_ID) == true
                )

    private fun IrSimpleFunction.loggerCall(
        context: IrPluginContext,
        level: IrEnumEntrySymbol,
        function0: IrClassSymbol,
    ): LoggerCall? {
        if (!hasShape(extensionReceiver = true, regularParameters = 3)) return null
        val receiver = parameters.first().takeIf { it.type.classOrNull == logger } ?: return null
        val message = parameter(AkkiNames.MESSAGE) ?: return null
        val isLazy = message.type.classOrNull == function0
        if (!isLazy && message.type != context.irBuiltIns.stringType) return null
        return LoggerCall(
            level = level,
            receiver = receiver,
            message = message,
            cause = parameter(AkkiNames.CAUSE) ?: return null,
            fields = parameter(AkkiNames.FIELDS) ?: return null,
            isLazy = isLazy,
        )
    }

    companion object {
        private const val SINK_SIGNATURE: String = "Logger.sink(level: Level): Sink?"
        private const val FOR_CALLER_SIGNATURE: String = "LogRegistry.forCaller(): Logger"
        private const val EMIT_SIGNATURE: String =
            "Sink.emit(message: String, cause: Throwable?, fields: Map<String, Any?>)"

        fun of(context: IrPluginContext): AkkiSymbols? {
            val finder = context.finderForBuiltins()
            finder.findClass(AkkiNames.LOGGER_ID) ?: return null
            return try {
                AkkiSymbols(context, finder)
            } catch (failure: IncompatibleCore) {
                context.diagnosticReporter.report(
                    AkkiErrors.INCOMPATIBLE_AKKI_CORE,
                    "The Akki compiler plugin cannot use the akki-core on the compile classpath: it does not " +
                        "declare '${failure.signature}'. The plugin and akki-core must come from the same version.",
                )
                null
            }
        }
    }
}

private class IncompatibleCore(val signature: String) : Exception(signature)

private fun incompatible(signature: String): Nothing = throw IncompatibleCore(signature)

private fun DeclarationFinder.classOrFail(id: ClassId): IrClassSymbol =
    findClass(id) ?: incompatible(id.asFqNameString())

private fun IrClassSymbol.functionOrFail(
    name: Name,
    signature: String,
    shape: (IrSimpleFunction) -> Boolean,
): IrSimpleFunction = owner.functions.singleOrNull { it.name == name && shape(it) } ?: incompatible(signature)

private fun IrSimpleFunction.parameter(name: Name): IrValueParameter? =
    nonDispatchParameters.singleOrNull { it.name == name }

private fun IrSimpleFunction.parameterOrFail(name: Name, signature: String, type: IrType? = null): IrValueParameter =
    nonDispatchParameters.singleOrNull { it.name == name && (type == null || it.type == type) }
        ?: incompatible(signature)

private fun IrType.arguments(): List<IrType> =
    (this as? IrSimpleType)?.arguments.orEmpty().map { it.typeOrFail }
