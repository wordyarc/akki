package io.akki.compiler.compat

import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.ir.IrDiagnosticReporter

public interface CompilerCompat {
    @CompatApi(
        since = "2.4.20-Beta1",
        change = "IrDiagnosticReporter.report of a sourceless diagnostic takes CompilerMessageSourceLocation",
    )
    public fun IrDiagnosticReporter.reportWithoutSource(
        diagnostic: KtSourcelessDiagnosticFactory,
        message: String,
    )

    public interface Factory {
        public val minVersion: String

        public fun create(): CompilerCompat
    }
}

@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
internal annotation class CompatApi(val since: String, val change: String)
