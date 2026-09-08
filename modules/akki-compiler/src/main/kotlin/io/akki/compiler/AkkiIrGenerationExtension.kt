package io.akki.compiler

import org.jetbrains.kotlin.backend.common.CompilationException
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.wrapWithCompilationException
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

internal class AkkiIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = AkkiSymbols.of(pluginContext) ?: return
        moduleFragment.lower(LoggerFieldLowering(pluginContext, symbols))
        moduleFragment.lower(LoggerCallLowering(pluginContext, symbols))
    }

    private fun IrModuleFragment.lower(lowering: FileLoweringPass) {
        files.forEach { lowering.lowerReportingFailures(it) }
    }

    private fun FileLoweringPass.lowerReportingFailures(irFile: IrFile) {
        try {
            lower(irFile)
        } catch (failure: CompilationException) {
            failure.initializeFileDetails(irFile)
            throw failure
        } catch (failure: VirtualMachineError) {
            throw failure
        } catch (failure: Throwable) {
            throw failure.wrapWithCompilationException(
                "The Akki compiler plugin failed in ${javaClass.simpleName}",
                irFile,
                null,
            )
        }
    }
}
