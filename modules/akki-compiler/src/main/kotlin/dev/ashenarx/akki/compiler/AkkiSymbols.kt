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
import org.jetbrains.kotlin.name.Name

internal class LoggerCall(
    val level: IrEnumEntrySymbol,
    val receiver: IrValueParameter,
    val message: IrValueParameter,
    val cause: IrValueParameter,
    val fields: IrValueParameter,
    val isLazy: Boolean,
)

internal class AkkiSymbols private constructor(
    val logger: IrClassSymbol,
    val loggerType: IrType,
    val logRegistry: IrClassSymbol,
    val forCaller: IrSimpleFunctionSymbol,
    val sink: IrSimpleFunctionSymbol,
    val emit: IrSimpleFunctionSymbol,
    val invoke: IrSimpleFunctionSymbol,
    val emptyMap: IrSimpleFunctionSymbol,
    val levelType: IrType,
    val causeType: IrType,
    val fieldsType: IrType,
    val fieldTypes: List<IrType>,
    val emitMessage: IrValueParameter,
    val emitCause: IrValueParameter,
    val emitFields: IrValueParameter,
    private val calls: Map<IrSimpleFunctionSymbol, LoggerCall>,
) {
    fun callFor(function: IrSimpleFunction): LoggerCall? = calls[function.symbol]

    fun isCallSite(function: IrSimpleFunction): Boolean =
        function.returnType.classOrNull == logger &&
            function.nonDispatchParameters.isEmpty() &&
            (
                function.hasAnnotation(AkkiNames.CALL_SITE_ID) ||
                    function.correspondingPropertySymbol?.owner?.hasAnnotation(AkkiNames.CALL_SITE_ID) == true
                )

    companion object {
        private const val INCOMPATIBLE_CORE: String =
            "The Akki compiler plugin cannot use the akki-core on the compile classpath: its API is not the one " +
                "the plugin generates calls against. The plugin and akki-core must come from the same version."

        fun of(context: IrPluginContext): AkkiSymbols? {
            val finder = context.finderForBuiltins()
            val logger = finder.findClass(AkkiNames.LOGGER_ID) ?: return null
            val symbols = resolve(context, finder, logger)
            if (symbols == null) {
                context.diagnosticReporter.report(AkkiErrors.INCOMPATIBLE_AKKI_CORE, INCOMPATIBLE_CORE)
            }
            return symbols
        }

        private fun resolve(
            context: IrPluginContext,
            finder: DeclarationFinder,
            logger: IrClassSymbol,
        ): AkkiSymbols? {
            val level = finder.findClass(AkkiNames.LEVEL_ID) ?: return null
            val sinkClass = finder.findClass(AkkiNames.SINK_ID) ?: return null
            val logRegistry = finder.findClass(AkkiNames.LOG_REGISTRY_ID) ?: return null

            val sink = logger.function(AkkiNames.SINK) {
                it.hasShape(dispatchReceiver = true, regularParameters = 1) &&
                    it.returnType.classOrNull == sinkClass &&
                    it.nonDispatchParameters.single().type.classOrNull == level
            } ?: return null
            val emit = sinkClass.function(AkkiNames.EMIT) {
                it.hasShape(dispatchReceiver = true, regularParameters = 3)
            } ?: return null
            val forCaller = logRegistry.function(AkkiNames.FOR_CALLER) {
                it.hasShape(dispatchReceiver = true) && it.returnType.classOrNull == logger
            } ?: return null

            val message = emit.parameter(AkkiNames.MESSAGE)?.takeIf { it.type == context.irBuiltIns.stringType }
                ?: return null
            val cause = emit.parameter(AkkiNames.CAUSE) ?: return null
            val fields = emit.parameter(AkkiNames.FIELDS) ?: return null
            val fieldTypes = fields.type.arguments().takeIf { it.size == 2 } ?: return null

            val emptyMap = finder.findFunctions(AkkiNames.EMPTY_MAP_ID).singleOrNull { it.owner.hasShape() }
                ?: return null
            val invoke = context.irBuiltIns.functionN(0).invokeFun ?: return null

            return AkkiSymbols(
                logger = logger,
                loggerType = logger.owner.defaultType,
                logRegistry = logRegistry,
                forCaller = forCaller.symbol,
                sink = sink.symbol,
                emit = emit.symbol,
                invoke = invoke.symbol,
                emptyMap = emptyMap,
                levelType = level.owner.defaultType,
                causeType = cause.type,
                fieldsType = fields.type,
                fieldTypes = fieldTypes,
                emitMessage = message,
                emitCause = cause,
                emitFields = fields,
                calls = levelCalls(context, finder, logger, level),
            )
        }

        private fun levelCalls(
            context: IrPluginContext,
            finder: DeclarationFinder,
            logger: IrClassSymbol,
            level: IrClassSymbol,
        ): Map<IrSimpleFunctionSymbol, LoggerCall> = buildMap {
            val function0 = context.irBuiltIns.functionN(0).symbol
            for (entry in level.owner.declarations.filterIsInstance<IrEnumEntry>()) {
                for (overload in finder.findFunctions(AkkiNames.levelId(entry.name))) {
                    val call = overload.owner.loggerCall(context, entry.symbol, logger, function0) ?: continue
                    put(overload, call)
                }
            }
        }

        private fun IrSimpleFunction.loggerCall(
            context: IrPluginContext,
            level: IrEnumEntrySymbol,
            logger: IrClassSymbol,
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

        private fun IrClassSymbol.function(name: Name, shape: (IrSimpleFunction) -> Boolean): IrSimpleFunction? =
            owner.functions.singleOrNull { it.name == name && shape(it) }

        private fun IrSimpleFunction.parameter(name: Name): IrValueParameter? =
            nonDispatchParameters.singleOrNull { it.name == name }
    }
}

private fun IrType.arguments(): List<IrType> =
    (this as? IrSimpleType)?.arguments.orEmpty().map { it.typeOrFail }
