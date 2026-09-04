@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
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
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.invokeFun
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
    val emitMessage: Int,
    val emitCause: Int,
    val emitFields: Int,
    private val calls: Map<IrSimpleFunctionSymbol, LoggerCall>,
) {
    fun callFor(function: IrSimpleFunction): LoggerCall? = calls[function.symbol]

    fun isCallSite(function: IrSimpleFunction): Boolean =
        function.returnType.classOrNull == logger &&
            function.regular.isEmpty() &&
            (
                function.hasAnnotation(CALL_SITE) ||
                    function.correspondingPropertySymbol?.owner?.hasAnnotation(CALL_SITE) == true
                )

    companion object {
        private val AKKI_PACKAGE = FqName("dev.ashenarx.akki")
        private val INTERNAL_PACKAGE = FqName("dev.ashenarx.akki.internal")
        private val CALL_SITE = FqName("dev.ashenarx.akki.internal.CallSite")
        private val LOGGER_ID = ClassId(AKKI_PACKAGE, Name.identifier("Logger"))
        private val LEVEL_ID = ClassId(AKKI_PACKAGE, Name.identifier("Level"))
        private val SINK_ID = ClassId(AKKI_PACKAGE, Name.identifier("Sink"))
        private val LOG_REGISTRY_ID = ClassId(INTERNAL_PACKAGE, Name.identifier("LogRegistry"))
        private val EMPTY_MAP_ID = CallableId(FqName("kotlin.collections"), Name.identifier("emptyMap"))
        private val FOR_CALLER = Name.identifier("forCaller")
        private val SINK = Name.identifier("sink")
        private val EMIT = Name.identifier("emit")
        private val MESSAGE = Name.identifier("message")
        private val CAUSE = Name.identifier("cause")
        private val FIELDS = Name.identifier("fields")

        private const val INCOMPATIBLE_CORE: String =
            "The Akki compiler plugin cannot use the akki-core on the compile classpath: its API is not the one " +
                "the plugin generates calls against. The plugin and akki-core must come from the same version."

        fun of(context: IrPluginContext): AkkiSymbols? {
            val finder = context.finderForBuiltins()
            val logger = finder.findClass(LOGGER_ID) ?: return null
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
            val level = finder.findClass(LEVEL_ID) ?: return null
            val sinkClass = finder.findClass(SINK_ID) ?: return null
            val logRegistry = finder.findClass(LOG_REGISTRY_ID) ?: return null

            val sink = logger.function(SINK) {
                it.returnType.classOrNull == sinkClass && it.regular.singleOrNull()?.type?.classOrNull == level
            } ?: return null
            val emit = sinkClass.function(EMIT) { it.regular.size == 3 } ?: return null
            val forCaller = logRegistry.function(FOR_CALLER) {
                it.regular.isEmpty() && it.returnType.classOrNull == logger
            } ?: return null

            val message = emit.parameter(MESSAGE)?.takeIf { it.type == context.irBuiltIns.stringType } ?: return null
            val cause = emit.parameter(CAUSE) ?: return null
            val fields = emit.parameter(FIELDS) ?: return null
            val fieldTypes = fields.type.arguments().takeIf { it.size == 2 } ?: return null

            val emptyMap = finder.findFunctions(EMPTY_MAP_ID).singleOrNull { it.owner.regular.isEmpty() }
                ?: return null
            val invoke = context.irBuiltIns.functionN(0).invokeFun ?: return null
            val calls = levelCalls(context, finder, logger, level) ?: return null

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
                emitMessage = message.indexInParameters,
                emitCause = cause.indexInParameters,
                emitFields = fields.indexInParameters,
                calls = calls,
            )
        }

        private fun levelCalls(
            context: IrPluginContext,
            finder: DeclarationFinder,
            logger: IrClassSymbol,
            level: IrClassSymbol,
        ): Map<IrSimpleFunctionSymbol, LoggerCall>? {
            val function0 = context.irBuiltIns.functionN(0).symbol
            val calls = mutableMapOf<IrSimpleFunctionSymbol, LoggerCall>()
            for (entry in level.owner.declarations.filterIsInstance<IrEnumEntry>()) {
                val id = CallableId(AKKI_PACKAGE, Name.identifier(entry.name.asString().lowercase()))
                val overloads = finder.findFunctions(id)
                    .filter { it.owner.parameters.firstOrNull()?.type?.classOrNull == logger }
                if (overloads.size != 2) return null
                for (overload in overloads) {
                    calls[overload] = overload.owner.loggerCall(context, entry.symbol, function0) ?: return null
                }
            }
            return calls
        }

        private fun IrSimpleFunction.loggerCall(
            context: IrPluginContext,
            level: IrEnumEntrySymbol,
            function0: IrClassSymbol,
        ): LoggerCall? {
            val message = parameter(MESSAGE) ?: return null
            val isLazy = message.type.classOrNull == function0
            if (!isLazy && message.type != context.irBuiltIns.stringType) return null
            return LoggerCall(
                level = level,
                message = message.indexInParameters,
                cause = (parameter(CAUSE) ?: return null).indexInParameters,
                fields = (parameter(FIELDS) ?: return null).indexInParameters,
                isLazy = isLazy,
            )
        }

        private fun IrClassSymbol.function(name: Name, shape: (IrSimpleFunction) -> Boolean): IrSimpleFunction? =
            owner.functions.singleOrNull { it.name == name && shape(it) }

        private fun IrSimpleFunction.parameter(name: Name): IrValueParameter? =
            regular.singleOrNull { it.name == name }
    }
}

internal val IrSimpleFunction.regular: List<IrValueParameter>
    get() = parameters.filter { it.kind == IrParameterKind.Regular }

private fun IrType.arguments(): List<IrType> =
    (this as? IrSimpleType)?.arguments.orEmpty().map { it.typeOrFail }
