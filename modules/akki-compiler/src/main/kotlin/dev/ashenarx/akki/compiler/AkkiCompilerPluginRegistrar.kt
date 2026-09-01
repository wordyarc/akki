@file:OptIn(ExperimentalCompilerApi::class)

package dev.ashenarx.akki.compiler

import dev.ashenarx.akki.compiler.fir.AkkiFirExtensionRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

internal const val AKKI_PLUGIN_ID: String = "dev.ashenarx.akki"

internal val AKKI_ENABLED: CompilerConfigurationKey<Boolean> = CompilerConfigurationKey.create("akki.enabled")

internal class AkkiCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = AKKI_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(ENABLED_OPTION)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option) {
            ENABLED_OPTION -> configuration.put(AKKI_ENABLED, value.toBoolean())
            else -> error("Unknown option: ${option.optionName}")
        }
    }

    private companion object {
        val ENABLED_OPTION = CliOption(
            optionName = "enabled",
            valueDescription = "true/false",
            description = "Rewrite Logger level calls into a single sink resolution. " +
                "Disable to fall back to the plain runtime implementation.",
            required = false,
            allowMultipleOccurrences = false,
        )
    }
}

internal class AkkiCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = AKKI_PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        if (configuration.get(AKKI_ENABLED) == false) return
        FirExtensionRegistrarAdapter.registerExtension(AkkiFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(AkkiIrGenerationExtension())
    }
}
