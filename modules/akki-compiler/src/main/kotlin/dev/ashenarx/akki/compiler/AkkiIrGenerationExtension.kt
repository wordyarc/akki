package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.CompilationException
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.utils.exceptions.rethrowIntellijPlatformExceptionIfNeeded

internal class AkkiIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = AkkiSymbols.of(pluginContext) ?: return
        val lowerings: List<FileLoweringPass> = listOf(
            LoggerFieldLowering(pluginContext, symbols),
            LoggerAliasLowering(),
            LoggerCallLowering(pluginContext, symbols),
        )
        for (lowering in lowerings) {
            moduleFragment.files.forEach { lowering.lowerReportingFailures(it) }
        }
    }

    private fun FileLoweringPass.lowerReportingFailures(irFile: IrFile) {
        try {
            lower(irFile)
        } catch (failure: Throwable) {
            rethrowIntellijPlatformExceptionIfNeeded(failure)
            throw when (failure) {
                is VirtualMachineError, is CompilationException -> failure
                else -> CompilationException(
                    "The Akki compiler plugin failed in ${javaClass.simpleName}, see cause",
                    irFile,
                    null,
                    failure,
                )
            }
        }
    }
}
