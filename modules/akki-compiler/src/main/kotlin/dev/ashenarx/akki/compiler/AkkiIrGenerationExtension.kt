package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

internal class AkkiIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext): Unit {
        for (file in moduleFragment.files) {
            LoggerCallLowering(pluginContext).visitFile(file)
        }
    }
}
