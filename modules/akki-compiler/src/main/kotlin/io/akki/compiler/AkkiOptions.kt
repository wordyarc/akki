@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler

import io.akki.compiler.compat.breaksLinkage
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.name.Name

internal enum class MinLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF,
    ;

    val option: String get() = name.lowercase()

    fun clips(level: Name): Boolean = entries.firstOrNull { it.name == level.asString() }?.let { it < this } == true

    companion object {
        val DEFAULT: MinLevel = TRACE

        fun parseOrNull(option: String): MinLevel? = entries.firstOrNull { it.option == option }
    }
}

internal object AkkiConfigurationKeys {
    val MIN_LEVEL: CompilerConfigurationKey<MinLevel> = CompilerConfigurationKey("akki minimal level")
}

internal class AkkiCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = AkkiNames.PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(MinLevelOption)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        if (option != MinLevelOption) throw CliOptionProcessingException("Unknown Akki option '${option.optionName}'")
        val level = MinLevel.parseOrNull(value)
            ?: throw CliOptionProcessingException(
                "Unknown value '$value' for the Akki option '${MinLevelOption.optionName}'. " +
                    "Expected one of ${MinLevel.entries.joinToString(", ") { it.option }}.",
            )
        try {
            configuration.put(AkkiConfigurationKeys.MIN_LEVEL, level)
        } catch (failure: LinkageError) {
            if (!failure.breaksLinkage) throw failure
            throw CliOptionProcessingException("$CANNOT_START ${unlinkedCompiler()}${causeLines(failure)}", failure)
        }
    }

    private object MinLevelOption : AbstractCliOption {
        override val optionName: String = "minLevel"

        override val valueDescription: String = MinLevel.entries.joinToString("|") { it.option }

        override val description: String =
            "Minimum level to retain in compiled code. Lower-level records are removed permanently. " +
                "Ordinary arguments are still evaluated, but lazy messages are not invoked. " +
                "Each removed call produces an INFO diagnostic. If the build log hides INFO diagnostics, use " +
                "-Xwarning-level=LOGGING_CALL_REMOVED:warning to display them."

        override val required: Boolean = false

        override val allowMultipleOccurrences: Boolean = false
    }
}
