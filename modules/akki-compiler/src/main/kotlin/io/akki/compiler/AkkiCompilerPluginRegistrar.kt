@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler

import io.akki.compiler.fir.AkkiFirExtensionRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.registerExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

internal class AkkiCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = AkkiNames.PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrar.registerExtension(AkkiFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(
            AkkiIrGenerationExtension(configuration[MIN_LEVEL, MinLevel.DEFAULT]),
        )
    }
}
