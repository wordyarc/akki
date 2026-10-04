package io.akki.compiler.compat.k2420

import io.akki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.ir.IrDiagnosticReporter

internal class CompilerCompatImpl : CompilerCompat {
    override fun IrDiagnosticReporter.reportWithoutSource(
        diagnostic: KtSourcelessDiagnosticFactory,
        message: String,
    ) {
        report(diagnostic, message, location = null)
    }
}
