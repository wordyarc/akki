@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
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

        fun of(option: String): MinLevel? = entries.firstOrNull { it.option == option }
    }
}

internal val MIN_LEVEL: CompilerConfigurationKey<MinLevel> = CompilerConfigurationKey.create("akki minimal level")

internal class AkkiCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = AkkiNames.PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(MIN_LEVEL_OPTION)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        if (option != MIN_LEVEL_OPTION) throw CliOptionProcessingException("Unknown Akki option '${option.optionName}'")
        val level = MinLevel.of(value)
            ?: throw CliOptionProcessingException(
                "Unknown value '$value' for the Akki option '${MIN_LEVEL_OPTION.optionName}'. " +
                    "Expected one of ${MinLevel.entries.joinToString(", ") { it.option }}.",
            )
        configuration.put(MIN_LEVEL, level)
    }

    private companion object {
        val MIN_LEVEL_OPTION: CliOption = CliOption(
            optionName = "minLevel",
            valueDescription = MinLevel.entries.joinToString("|") { it.option },
            description = "Lowest level kept at compile time. Calls below it are removed from the bytecode " +
                "together with their arguments and message, and no runtime configuration can bring them back. " +
                "Every removed call is reported as an info; pass " +
                "-Xwarning-level=LOGGING_CALL_REMOVED:warning to see the reports in a build log that hides infos.",
            required = false,
        )
    }
}
