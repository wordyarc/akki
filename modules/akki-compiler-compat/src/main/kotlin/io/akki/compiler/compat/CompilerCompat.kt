package io.akki.compiler.compat

import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile

public interface CompilerCompat {
    @CompatApi(
        since = "2.4.20-Beta1",
        change = "IrDiagnosticReporter.report of a sourceless diagnostic takes CompilerMessageSourceLocation",
    )
    public fun IrDiagnosticReporter.reportWithoutSource(
        diagnostic: KtSourcelessDiagnosticFactory,
        message: String,
    )

    @CompatApi(
        since = "2.5.0-Beta2",
        change = "IrDiagnosticReporter.at of an element and its file takes the source element",
    )
    public fun IrDiagnosticReporter.diagnosticContext(
        element: IrElement,
        file: IrFile,
    ): IrDiagnosticReporter.IrDiagnosticContext

    public interface Factory {
        public val minVersion: String

        public fun create(): CompilerCompat
    }
}

@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
internal annotation class CompatApi(val since: String, val change: String)
