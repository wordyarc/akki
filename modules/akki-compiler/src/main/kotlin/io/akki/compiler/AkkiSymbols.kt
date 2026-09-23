@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.akki.compiler

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
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
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.hasShape
import org.jetbrains.kotlin.ir.util.invokeFun
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
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

    private val log: IrClassSymbol = finder.classOrFail(AkkiNames.LOG_ID)

    private val level: IrClassSymbol = finder.classOrFail(AkkiNames.LEVEL_ID)
    private val sinkClass: IrClassSymbol = finder.classOrFail(AkkiNames.SINK_ID)

    val levelType: IrType = level.owner.defaultType

    val sink: IrSimpleFunctionSymbol =
        logger.functionOrFail(AkkiNames.SINK, SINK_SIGNATURE, parameters = 1).symbol

    val forDeclaration: IrSimpleFunctionSymbol =
        logRegistry.functionOrFail(AkkiNames.FOR_DECLARATION, FOR_DECLARATION_SIGNATURE, parameters = 2).symbol

    val registryOf: IrSimpleFunctionSymbol =
        logRegistry.functionOrFail(AkkiNames.OF, REGISTRY_OF_SIGNATURE, parameters = 1).symbol

    val ofType: IrSimpleFunctionSymbol = log.owner.functions
        .singleOrNull {
            it.name == AkkiNames.OF &&
                it.hasShape(dispatchReceiver = true, regularParameters = 1) &&
                it.parameters[1].type.classOrNull?.owner?.classId == StandardClassIds.KClass
        }
        ?.symbol
        ?: incompatible(OF_TYPE_SIGNATURE)

    val ofReifiedType: IrSimpleFunctionSymbol =
        log.functionOrFail(AkkiNames.OF, OF_REIFIED_SIGNATURE, parameters = 0).symbol

    val named: IrSimpleFunctionSymbol = log.functionOrFail(AkkiNames.NAMED, NAMED_SIGNATURE, parameters = 1).symbol

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

    fun callFor(function: IrSimpleFunction): LoggerCall? = calls[function.resolveFakeOverride()?.symbol]

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
        return listOf(
            EmitArgument(EmitSlot.MESSAGE, message, toMessage),
            EmitArgument(EmitSlot.CAUSE, cause, toCause),
            EmitArgument(EmitSlot.FIELDS, fields, toFields),
        ).sortedBy { it.source.indexInParameters }
    }

    companion object {
        private const val SINK_SIGNATURE: String = "Logger.sink(level: Level): Sink?"
        private const val FOR_DECLARATION_SIGNATURE: String =
            "LogRegistry.forDeclaration(source: String, platformName: String): Logger"
        private const val REGISTRY_OF_SIGNATURE: String = "LogRegistry.of(name: String): Logger"
        private const val OF_TYPE_SIGNATURE: String = "Log.of(type: KClass<*>): Logger"
        private const val OF_REIFIED_SIGNATURE: String = "Log.of<T>(): Logger"
        private const val NAMED_SIGNATURE: String = "Log.named(name: String): Logger"
        private const val EMIT_SIGNATURE: String =
            "Sink.emit(message: String, cause: Throwable?, fields: Map<String, Any?>)"

        fun of(context: IrPluginContext): AkkiSymbols? {
            val finder = context.finderForBuiltins()
            finder.findClass(AkkiNames.LOGGER_ID) ?: return null
            val coreVersion = finder.findClass(AkkiNames.LOG_REGISTRY_ID)?.owner?.declaredVersion()
            if (coreVersion != null && coreVersion != AKKI_VERSION) {
                return context.incompatible(
                    "The Akki compiler plugin $AKKI_VERSION cannot use akki-core $coreVersion on the compile " +
                        "classpath: the plugin and akki-core must come from the same version.",
                )
            }
            return try {
                AkkiSymbols(context, finder)
            } catch (failure: IncompatibleCore) {
                context.incompatible(
                    "The Akki compiler plugin $AKKI_VERSION cannot use the akki-core on the compile classpath: " +
                        "it does not state its version and does not declare '${failure.signature}'. " +
                        "The plugin and akki-core must come from the same version.",
                )
            }
        }

        private fun IrPluginContext.incompatible(message: String): AkkiSymbols? {
            diagnosticReporter.report(AkkiErrors.INCOMPATIBLE_AKKI_CORE, message)
            return null
        }

        private fun IrClass.declaredVersion(): String? =
            properties.singleOrNull { it.name == AkkiNames.VERSION }?.constantString()
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
