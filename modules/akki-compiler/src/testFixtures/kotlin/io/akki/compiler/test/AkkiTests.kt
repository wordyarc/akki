package io.akki.compiler.test

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.backend.handlers.UpdateTestDataHandler
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirBlackBoxCodegenTestBase
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

abstract class AbstractAkkiBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            configureAkki()
            defaultDirectives {
                DiagnosticsDirectives.DIAGNOSTICS with "-infos"
            }
            useAfterAnalysisCheckers(::UpdateTestDataHandler)
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
            useAfterAnalysisCheckers(::SourcelessDiagnosticsChecker, ::UpdateTestDataHandler)
        }
    }
}
