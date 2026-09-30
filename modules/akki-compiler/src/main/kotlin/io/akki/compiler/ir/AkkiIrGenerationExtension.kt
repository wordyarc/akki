package io.akki.compiler.ir

import io.akki.compiler.MinLevel
import io.akki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

internal class AkkiIrGenerationExtension(
    private val minLevel: MinLevel,
    private val compat: CompilerCompat,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = AkkiSymbols.of(pluginContext, compat) ?: return
        LoggerFieldLowering(pluginContext, symbols).lower(moduleFragment)
        LoggerCallLowering(pluginContext, symbols, minLevel, compat).lower(moduleFragment)
    }
}
