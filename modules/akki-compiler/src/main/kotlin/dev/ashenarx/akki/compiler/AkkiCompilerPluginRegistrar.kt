package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

internal class AkkiCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "dev.ashenarx.akki"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration): Unit {
        IrGenerationExtension.registerExtension(AkkiIrGenerationExtension())
    }
}
