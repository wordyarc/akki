@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler.compat.fixture

import io.akki.compiler.compat.CompilerCompat
import io.akki.compiler.compat.ReflectiveCompilerCompatFactory
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

internal class CurrentAdapter : InertAdapter()

internal class CurrentFactory : ReflectiveCompilerCompatFactory("2.3.20", "$FIXTURE_PACKAGE.CurrentAdapter")

internal class FutureAdapter : InertAdapter()

internal class FutureFactory : ReflectiveCompilerCompatFactory("9.0.0", "$FIXTURE_PACKAGE.FutureAdapter")

internal class AbsentFactory : ReflectiveCompilerCompatFactory("9.0.0", "$FIXTURE_PACKAGE.AbsentAdapter")

internal class ForeignAdapter

internal class ForeignFactory : ReflectiveCompilerCompatFactory("9.0.0", "$FIXTURE_PACKAGE.ForeignAdapter")

internal class ParameterizedAdapter(val name: String) : InertAdapter()

internal class ParameterizedFactory : ReflectiveCompilerCompatFactory("9.0.0", "$FIXTURE_PACKAGE.ParameterizedAdapter")

internal class PrivateAdapter private constructor() : InertAdapter()

internal class PrivateFactory : ReflectiveCompilerCompatFactory("9.0.0", "$FIXTURE_PACKAGE.PrivateAdapter")

internal class FailingAdapter : InertAdapter() {
    init {
        throw IllegalStateException("adapter constructor failed")
    }
}

internal class FailingFactory : ReflectiveCompilerCompatFactory("9.0.0", "$FIXTURE_PACKAGE.FailingAdapter")

internal class ExhaustedAdapter : InertAdapter() {
    init {
        throw InternalError("virtual machine failed")
    }
}

internal class ExhaustedFactory : ReflectiveCompilerCompatFactory("9.0.0", "$FIXTURE_PACKAGE.ExhaustedAdapter")

internal class BrokenAdapter : InertAdapter() {
    private companion object {
        init {
            throw NoSuchMethodError("compiler API is missing")
        }
    }
}

internal class BrokenFactory : ReflectiveCompilerCompatFactory("9.0.0", "$FIXTURE_PACKAGE.BrokenAdapter")

internal class CrashingFactory : ReflectiveCompilerCompatFactory("2.3.20", "$FIXTURE_PACKAGE.CurrentAdapter") {
    init {
        throw InternalError("virtual machine failed in a factory")
    }
}

internal class UnlinkedFactory : ReflectiveCompilerCompatFactory("2.3.20", "$FIXTURE_PACKAGE.CurrentAdapter") {
    private companion object {
        init {
            throw NoSuchMethodError("compiler API is missing in a factory")
        }
    }
}

internal class MalformedFactory : ReflectiveCompilerCompatFactory("nine", "$FIXTURE_PACKAGE.CurrentAdapter")

internal class RivalFactory : ReflectiveCompilerCompatFactory("2.3.20", "$FIXTURE_PACKAGE.CurrentAdapter")
