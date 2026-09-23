package io.akki.compiler.test

import io.akki.compiler.MinLevel
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.backend.handlers.UpdateTestDataHandler
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest
import org.jetbrains.kotlin.test.runners.codegen.AbstractJvmBlackBoxCodegenTestBase
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.junit.jupiter.api.parallel.Isolated

abstract class AbstractAkkiBoxTest : AbstractJvmBlackBoxCodegenTestBase(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            configureAkki()
            defaultDirectives {
                DiagnosticsDirectives.DIAGNOSTICS with "-infos"
            }
            useFailureSuppressors(::UpdateTestDataHandler)
        }
    }
}

abstract class AbstractAkkiPluginlessBoxTest : AbstractAkkiBoxTest() {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.defaultDirectives {
            +AkkiDirectives.WITHOUT_PLUGIN
        }
    }
}

@Isolated
abstract class AbstractAkkiBackendBoxTest : AbstractAkkiBoxTest()

abstract class AbstractAkkiClippedBoxTest : AbstractAkkiBoxTest() {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.defaultDirectives {
            AkkiDirectives.MIN_LEVEL with MinLevel.INFO
        }
    }
}

abstract class AbstractAkkiDiagnosticsTest : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            configureAkki()
            defaultDirectives {
                +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
            }
            configureFirHandlersStep {
                useHandlers(::FirSourcelessDiagnosticsHandler)
            }
            configureIrHandlersStep {
                useHandlers(::IrSourcelessDiagnosticsHandler)
            }
            useAfterAnalysisCheckers(::SourcelessDiagnosticsChecker)
            useFailureSuppressors(::UpdateTestDataHandler)
        }
    }
}
