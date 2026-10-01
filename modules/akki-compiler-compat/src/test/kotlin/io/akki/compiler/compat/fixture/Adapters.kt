@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler.compat.fixture

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

internal const val FIXTURE_PACKAGE: String = "io.akki.compiler.compat.fixture"

internal abstract class InertAdapter : CompilerCompat {
    override fun registerExtensions(
        storage: CompilerPluginRegistrar.ExtensionStorage,
        fir: FirExtensionRegistrar,
        ir: IrGenerationExtension,
    ) = Unit

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

internal abstract class FixtureFactory(final override val minVersion: String) : CompilerCompat.Factory

internal class CurrentAdapter : InertAdapter()

internal class CurrentFactory : FixtureFactory("2.3.20") {
    override fun create(): CompilerCompat = CurrentAdapter()
}

internal class FutureAdapter : InertAdapter()

internal class FutureFactory : FixtureFactory("9.0.0") {
    override fun create(): CompilerCompat = FutureAdapter()
}

internal class FailingAdapter : InertAdapter() {
    init {
        throw IllegalStateException("adapter constructor failed")
    }
}

internal class FailingFactory : FixtureFactory("9.0.0") {
    override fun create(): CompilerCompat = FailingAdapter()
}

internal class ExhaustedAdapter : InertAdapter() {
    init {
        throw InternalError("virtual machine failed")
    }
}

internal class ExhaustedFactory : FixtureFactory("9.0.0") {
    override fun create(): CompilerCompat = ExhaustedAdapter()
}

internal class BrokenAdapter : InertAdapter() {
    private companion object {
        init {
            throw NoSuchMethodError("compiler API is missing")
        }
    }
}

internal class BrokenFactory : FixtureFactory("9.0.0") {
    override fun create(): CompilerCompat = BrokenAdapter()
}

internal class CrashingFactory : FixtureFactory("2.3.20") {
    init {
        throw InternalError("virtual machine failed in a factory")
    }

    override fun create(): CompilerCompat = CurrentAdapter()
}

internal class UnlinkedFactory : FixtureFactory("2.3.20") {
    override fun create(): CompilerCompat = CurrentAdapter()

    private companion object {
        init {
            throw NoSuchMethodError("compiler API is missing in a factory")
        }
    }
}

internal class MalformedFactory : FixtureFactory("nine") {
    override fun create(): CompilerCompat = CurrentAdapter()
}

internal class RivalFactory : FixtureFactory("2.3.20") {
    override fun create(): CompilerCompat = CurrentAdapter()
}
