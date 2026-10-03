package io.akki.compiler.compat.fixture

import io.akki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.ir.IrDiagnosticReporter

internal const val FIXTURE_PACKAGE: String = "io.akki.compiler.compat.fixture"

internal abstract class InertAdapter : CompilerCompat {
    override fun IrDiagnosticReporter.reportWithoutSource(
        diagnostic: KtSourcelessDiagnosticFactory,
        message: String,
    ) = Unit
}

internal abstract class FixtureFactory(final override val minVersion: String) : CompilerCompat.Factory

internal class CurrentAdapter : InertAdapter()

internal class CurrentFactory : FixtureFactory("2.3.20-Beta1") {
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

internal class UninitializableAdapter : InertAdapter() {
    private companion object {
        init {
            throw IllegalStateException("adapter initializer failed")
        }
    }
}

internal class UninitializableFactory : FixtureFactory("9.0.0") {
    override fun create(): CompilerCompat = UninitializableAdapter()
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

internal class RivalFactory : FixtureFactory("2.3.20-Beta1") {
    override fun create(): CompilerCompat = CurrentAdapter()
}
