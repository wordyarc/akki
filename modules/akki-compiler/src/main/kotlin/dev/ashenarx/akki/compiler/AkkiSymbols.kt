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
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
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
        private val LEVEL_NAMES = listOf("trace", "debug", "info", "warn", "error")
        private val FOR_CALLER = Name.identifier("forCaller")
        private val SINK = Name.identifier("sink")
        private val EMIT = Name.identifier("emit")
        private val INVOKE = Name.identifier("invoke")
        private val MESSAGE = Name.identifier("message")
        private val CAUSE = Name.identifier("cause")
        private val FIELDS = Name.identifier("fields")

        fun of(context: IrPluginContext): AkkiSymbols? {
            val finder = context.finderForBuiltins()
            val logger = finder.findClass(LOGGER_ID) ?: return null
            val level = finder.findClass(LEVEL_ID) ?: missing("enum class ${LEVEL_ID.asFqNameString()}")
            val sinkClass = finder.findClass(SINK_ID) ?: missing("interface ${SINK_ID.asFqNameString()}")
            val logRegistry = finder.findClass(LOG_REGISTRY_ID)
                ?: missing("object ${LOG_REGISTRY_ID.asFqNameString()}")

            val sink = logger.function(SINK, arity = 1)
            val emit = sinkClass.function(EMIT, arity = 3)
            val forCaller = logRegistry.function(FOR_CALLER, arity = 0)
            val emptyMap = finder.findFunctions(EMPTY_MAP_ID).singleOrNull { it.owner.regular.isEmpty() }
                ?: missing("function ${EMPTY_MAP_ID.asSingleFqName()} without parameters")

            val emitMessage = emit.parameter(MESSAGE)
            val emitCause = emit.parameter(CAUSE)
            val emitFields = emit.parameter(FIELDS)
            check(emitMessage.type == context.irBuiltIns.stringType) { emitMessage.mismatch("kotlin.String") }
            check(emitFields.type.typeArguments()?.size == 2) { emitFields.mismatch("kotlin.collections.Map") }
            check(sink.regular.single().type.classOrNull == level) {
                sink.regular.single().mismatch(LEVEL_ID.asFqNameString())
            }
            check(sink.returnType.classOrNull == sinkClass) { sink.returns(SINK_ID.asFqNameString()) }
            check(forCaller.returnType.classOrNull == logger) { forCaller.returns(LOGGER_ID.asFqNameString()) }

            return AkkiSymbols(
                logger = logger,
                loggerType = logger.owner.defaultType,
                logRegistry = logRegistry,
                forCaller = forCaller.symbol,
                sink = sink.symbol,
                emit = emit.symbol,
                invoke = context.irBuiltIns.functionN(0).symbol.function(INVOKE, arity = 0).symbol,
                emptyMap = emptyMap,
                levelType = level.owner.defaultType,
                causeType = emitCause.type,
                fieldsType = emitFields.type,
                emitMessage = emitMessage.indexInParameters,
                emitCause = emitCause.indexInParameters,
                emitFields = emitFields.indexInParameters,
                calls = levelCalls(context, finder, logger, level),
            )
        }

        private fun levelCalls(
            context: IrPluginContext,
            finder: DeclarationFinder,
            logger: IrClassSymbol,
            level: IrClassSymbol,
        ): Map<IrSimpleFunctionSymbol, LoggerCall> {
            val entries = level.owner.declarations
                .filterIsInstance<IrEnumEntry>()
                .associateBy { it.name.asString() }
            val function0 = context.irBuiltIns.functionN(0).symbol
            return LEVEL_NAMES.flatMap { name ->
                val id = CallableId(AKKI_PACKAGE, Name.identifier(name))
                val entry = entries[name.uppercase()]
                    ?: missing("entry ${name.uppercase()} of ${LEVEL_ID.asFqNameString()}")
                val overloads = finder.findFunctions(id)
                    .filter { it.owner.parameters.firstOrNull()?.type?.classOrNull == logger }
                check(overloads.size == 2) {
                    missingMessage(
                        "an eager and a lazy '${id.asSingleFqName()}' extension on ${LOGGER_ID.asFqNameString()}",
                    )
                }
                overloads.map { it to it.owner.loggerCall(context, entry.symbol, function0) }
            }.toMap()
        }

        private fun IrSimpleFunction.loggerCall(
            context: IrPluginContext,
            level: IrEnumEntrySymbol,
            function0: IrClassSymbol,
        ): LoggerCall {
            val message = parameter(MESSAGE)
            val isLazy = message.type.classOrNull == function0
            check(isLazy || message.type == context.irBuiltIns.stringType) {
                message.mismatch("kotlin.String or kotlin.Function0<kotlin.String>")
            }
            return LoggerCall(
                level = level,
                message = message.indexInParameters,
                cause = parameter(CAUSE).indexInParameters,
                fields = parameter(FIELDS).indexInParameters,
                isLazy = isLazy,
            )
        }

        private fun IrClassSymbol.function(name: Name, arity: Int): IrSimpleFunction =
            owner.functions.singleOrNull { it.name == name && it.regular.size == arity }
                ?: missing("function $name with $arity parameters in ${owner.kotlinFqName}")

        private fun IrSimpleFunction.parameter(name: Name): IrValueParameter =
            regular.singleOrNull { it.name == name } ?: missing("parameter '$name' of ${description()}")

        private fun IrSimpleFunction.description(): String = fqNameWhenAvailable?.asString() ?: name.asString()

        private fun IrSimpleFunction.returns(expected: String): String =
            missingMessage("${description()} returning $expected")

        private fun IrValueParameter.mismatch(expected: String): String {
            val owner = (parent as? IrSimpleFunction)?.description() ?: parent.kotlinFqName.asString()
            return missingMessage("parameter '$name' of $owner typed as $expected")
        }

        private fun missing(declaration: String): Nothing = error(missingMessage(declaration))

        private fun missingMessage(declaration: String): String =
            "The Akki compiler plugin cannot use the akki-core on the compile classpath: it provides no " +
                "$declaration. The plugin and akki-core must come from the same version."
    }
}

internal val IrSimpleFunction.regular: List<IrValueParameter>
    get() = parameters.filter { it.kind == IrParameterKind.Regular }

internal fun IrType.typeArguments(): List<IrType>? {
    val arguments = (this as? IrSimpleType)?.arguments ?: return null
    return arguments.map { it.typeOrNull ?: return null }
}
