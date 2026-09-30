@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler.compat.k2420

import io.akki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile

public class CompilerCompatImpl : CompilerCompat {
    override fun registerExtensions(
        storage: CompilerPluginRegistrar.ExtensionStorage,
        fir: FirExtensionRegistrar,
        ir: IrGenerationExtension,
    ) {
        with(storage) {
            FirExtensionRegistrarAdapter.registerExtension(fir)
            IrGenerationExtension.registerExtension(ir)
        }
    }

    override fun <A : Any, B : Any> IrDiagnosticReporter.reportAt(
        element: IrElement,
        file: IrFile,
        diagnostic: KtDiagnosticFactory2<A, B>,
        first: A,
        second: B,
    ) {
        at(element, file).report(diagnostic, first, second)
    }

    override fun IrDiagnosticReporter.reportWithoutSource(
        diagnostic: KtSourcelessDiagnosticFactory,
        message: String,
    ) {
        report(diagnostic, message, location = null)
    }
}
