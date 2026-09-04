package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.wrapWithCompilationException
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class AkkiIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = AkkiSymbols.of(pluginContext) ?: return
        val lowerings = listOf(
            LoggerFieldLowering(pluginContext, symbols),
            LoggerAliasLowering(),
            LoggerCallLowering(pluginContext, symbols),
        )
        for (lowering in lowerings) {
            moduleFragment.files.forEach { it.lower(lowering) }
        }
    }

    private fun IrFile.lower(lowering: IrElementTransformerVoid) {
        try {
            transform(lowering, null)
        } catch (e: Throwable) {
            throw e.wrapWithCompilationException("Akki compiler plugin internal error", this, null)
        }
    }
}
