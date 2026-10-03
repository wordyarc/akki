package io.akki.compiler

import io.akki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.ir.IrDiagnosticReporter

internal class UninitializableAdapter : CompilerCompat {
    override fun IrDiagnosticReporter.reportWithoutSource(
        diagnostic: KtSourcelessDiagnosticFactory,
        message: String,
    ) = Unit

    private companion object {
        init {
            throw IllegalStateException("adapter initializer failed")
        }
    }
}

internal class UninitializableFactory : CompilerCompat.Factory {
    override val minVersion: String = "2.4.0-Beta1"

    override fun create(): CompilerCompat = UninitializableAdapter()
}

internal class UnloadableFactory : CompilerCompat.Factory {
    override val minVersion: String = "2.4.0-Beta1"

    override fun create(): CompilerCompat = throw NoClassDefFoundError("org/jetbrains/kotlin/Removed")
}
