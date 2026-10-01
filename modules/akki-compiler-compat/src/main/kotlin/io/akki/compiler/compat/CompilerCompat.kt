@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler.compat

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile

public interface CompilerCompat {
    @CompatApi(
        since = "2.4.0-Beta1",
        change = "ExtensionStorage.registerExtension takes ExtensionPointDescriptor, not ProjectExtensionDescriptor",
    )
    public fun registerExtensions(
        storage: CompilerPluginRegistrar.ExtensionStorage,
        fir: FirExtensionRegistrar,
        ir: IrGenerationExtension,
    )

    @CompatApi(
        since = "2.4.0-Beta1",
        change = "IrDiagnosticReporter.at returns IrDiagnosticContext, not DiagnosticContextImpl",
    )
    public fun <A : Any, B : Any> IrDiagnosticReporter.reportAt(
        element: IrElement,
        file: IrFile,
        diagnostic: KtDiagnosticFactory2<A, B>,
        first: A,
        second: B,
    )

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

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
internal annotation class CompatApi(val since: String, val change: String)
