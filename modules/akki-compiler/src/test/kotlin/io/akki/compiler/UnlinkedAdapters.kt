@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler

import io.akki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile

internal abstract class FailingRegistration(private val failure: () -> Throwable) : CompilerCompat {
    override fun registerExtensions(
        storage: CompilerPluginRegistrar.ExtensionStorage,
        fir: FirExtensionRegistrar,
        ir: IrGenerationExtension,
    ) {
        throw failure()
    }

    override fun <A : Any, B : Any> IrDiagnosticReporter.reportAt(
        element: IrElement,
        file: IrFile,
        diagnostic: KtDiagnosticFactory2<A, B>,
        first: A,
        second: B,
    ) = Unit

    override fun IrDiagnosticReporter.reportWithoutSource(
        diagnostic: KtSourcelessDiagnosticFactory,
        message: String,
    ) = Unit
}

internal class UnlinkedAdapter : FailingRegistration({ NoSuchMethodError("registerExtension") })

internal class MiscastAdapter : FailingRegistration({ ClassCastException("ProjectExtensionDescriptor") })

internal class UnlinkedFactory : CompilerCompat.Factory {
    override val minVersion: String = "2.3.20-Beta1"

    override fun create(): CompilerCompat = UnlinkedAdapter()
}

internal class MiscastFactory : CompilerCompat.Factory {
    override val minVersion: String = "2.3.20-Beta1"

    override fun create(): CompilerCompat = MiscastAdapter()
}

internal class InitializerBugAdapter :
    FailingRegistration({ ExceptionInInitializerError(IllegalStateException("adapter initializer failed")) })

internal class InitializerBugFactory : CompilerCompat.Factory {
    override val minVersion: String = "2.3.20-Beta1"

    override fun create(): CompilerCompat = InitializerBugAdapter()
}

internal class UninitializableAdapter : FailingRegistration({ IllegalStateException("never registers") }) {
    private companion object {
        init {
            throw IllegalStateException("adapter initializer failed")
        }
    }
}

internal class UninitializableFactory : CompilerCompat.Factory {
    override val minVersion: String = "2.3.20-Beta1"

    override fun create(): CompilerCompat = UninitializableAdapter()
}

internal class UnloadableFactory : CompilerCompat.Factory {
    override val minVersion: String = "2.3.20-Beta1"

    override fun create(): CompilerCompat = throw NoClassDefFoundError("org/jetbrains/kotlin/Removed")
}
