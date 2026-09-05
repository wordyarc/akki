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
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.hasShape
import org.jetbrains.kotlin.ir.util.invokeFun
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal enum class EmitSlot {
    MESSAGE,
    CAUSE,
    FIELDS,
}

internal class EmitArgument(
    val slot: EmitSlot,
    val source: IrValueParameter,
    val destination: IrValueParameter,
    val mustFreeze: Boolean,
)

internal class LoggerCall(
    val level: IrEnumEntrySymbol,
    val receiver: IrValueParameter,
    val message: IrValueParameter,
    val isLazy: Boolean,
    val arguments: List<EmitArgument>,
)

internal class AkkiSymbols private constructor(context: IrPluginContext, finder: DeclarationFinder) {
    val logger: IrClassSymbol = finder.classOrFail(AkkiNames.LOGGER_ID)
    val loggerType: IrType = logger.owner.defaultType
    val logRegistry: IrClassSymbol = finder.classOrFail(AkkiNames.LOG_REGISTRY_ID)

    private val level: IrClassSymbol = finder.classOrFail(AkkiNames.LEVEL_ID)
    private val sinkClass: IrClassSymbol = finder.classOrFail(AkkiNames.SINK_ID)

    val levelType: IrType = level.owner.defaultType

    val sink: IrSimpleFunctionSymbol =
        logger.functionOrFail(AkkiNames.SINK, SINK_SIGNATURE, parameters = 1).symbol

    val forCaller: IrSimpleFunctionSymbol =
        logRegistry.functionOrFail(AkkiNames.FOR_CALLER, FOR_CALLER_SIGNATURE, parameters = 0).symbol

    private val emitFunction: IrSimpleFunction =
        sinkClass.functionOrFail(AkkiNames.EMIT, EMIT_SIGNATURE, parameters = 3)

    val emit: IrSimpleFunctionSymbol = emitFunction.symbol

    val emptyMap: IrSimpleFunctionSymbol = finder.findFunctions(AkkiNames.EMPTY_MAP_ID)
        .singleOrNull { it.owner.hasShape() }
        ?: incompatible("kotlin.collections.emptyMap()")

    val invoke: IrSimpleFunctionSymbol =
        (context.irBuiltIns.functionN(0).invokeFun ?: incompatible("kotlin.Function0.invoke()")).symbol

    private val calls: Map<IrSimpleFunctionSymbol, LoggerCall> = buildMap {
        val function0 = context.irBuiltIns.functionN(0).symbol
        val overloads = logger.owner.functions.groupBy(IrSimpleFunction::name)
        for (entry in level.owner.declarations.filterIsInstance<IrEnumEntry>()) {
            for (overload in overloads[AkkiNames.levelName(entry.name)].orEmpty()) {
                val call = overload.loggerCall(context, entry.symbol, function0) ?: continue
                put(overload.symbol, call)
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
        if (!hasShape(dispatchReceiver = true, regularParameters = 3)) return null
        val receiver = parameters.first().takeIf { it.type.classOrNull == logger } ?: return null
        val message = parameter(AkkiNames.MESSAGE) ?: return null
        val isLazy = message.type.classOrNull == function0
        if (!isLazy && message.type != context.irBuiltIns.stringType) return null
        return LoggerCall(
            level = level,
            receiver = receiver,
            message = message,
            isLazy = isLazy,
            arguments = emitArguments(
                message = message,
                cause = parameter(AkkiNames.CAUSE) ?: return null,
                fields = parameter(AkkiNames.FIELDS) ?: return null,
            ),
        )
    }

    private fun emitArguments(
        message: IrValueParameter,
        cause: IrValueParameter,
        fields: IrValueParameter,
    ): List<EmitArgument> {
        val (toMessage, toCause, toFields) = emitFunction.nonDispatchParameters
        val ordered = listOf(
            Triple(EmitSlot.MESSAGE, message, toMessage),
            Triple(EmitSlot.CAUSE, cause, toCause),
            Triple(EmitSlot.FIELDS, fields, toFields),
        ).sortedBy { (_, source, _) -> source.indexInParameters }
        val reordered = ordered.map { (_, _, destination) -> destination.indexInParameters }
            .zipWithNext()
            .any { (previous, next) -> previous > next }
        return ordered.mapIndexed { position, (slot, source, destination) ->
            EmitArgument(slot, source, destination, mustFreeze = reordered && position < ordered.lastIndex)
        }
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

private fun IrClassSymbol.functionOrFail(name: Name, signature: String, parameters: Int): IrSimpleFunction =
    owner.functions.singleOrNull {
        it.name == name && it.hasShape(dispatchReceiver = true, regularParameters = parameters)
    } ?: incompatible(signature)

private fun IrSimpleFunction.parameter(name: Name): IrValueParameter? =
    nonDispatchParameters.singleOrNull { it.name == name }
