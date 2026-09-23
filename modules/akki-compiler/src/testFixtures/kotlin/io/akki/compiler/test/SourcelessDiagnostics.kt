package io.akki.compiler.test

import io.akki.compiler.AKKI_VERSION
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.test.backend.handlers.AbstractIrHandler
import org.jetbrains.kotlin.test.backend.handlers.assertFileDoesntExist
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.frontend.fir.FirCliBasedOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirAnalysisHandler
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.ServiceRegistrationData
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlin.test.services.service
import org.jetbrains.kotlin.test.utils.withExtension

internal class SourcelessDiagnostics : TestService {
    private val rendered = mutableListOf<String>()

    fun collect(module: TestModule, collector: BaseDiagnosticsCollector) {
        collector.diagnosticsByFile[null].orEmpty().mapTo(rendered) { diagnostic ->
            "${module.name}: ${diagnostic.severity.name.lowercase()}: [${diagnostic.factoryName}] " +
                diagnostic.renderMessage().replace(AKKI_VERSION, "<version>")
        }
    }

    fun dump(): String = rendered.joinToString("\n")
}

private val TestServices.sourcelessDiagnostics: SourcelessDiagnostics by TestServices.testServiceAccessor()

internal class FirSourcelessDiagnosticsHandler(testServices: TestServices) : FirAnalysisHandler(testServices) {
    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::SourcelessDiagnostics))

    override fun processModule(module: TestModule, info: FirOutputArtifact) {
        val artifact = info as? FirCliBasedOutputArtifact<*> ?: return
        testServices.sourcelessDiagnostics.collect(module, artifact.cliArtifact.configuration.diagnosticsCollector)
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) = Unit
}

internal class IrSourcelessDiagnosticsHandler(testServices: TestServices) : AbstractIrHandler(testServices) {
    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::SourcelessDiagnostics))

    override fun processModule(module: TestModule, info: IrBackendInput) {
        testServices.sourcelessDiagnostics.collect(module, info.diagnosticReporter)
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) = Unit
}

internal class SourcelessDiagnosticsChecker(testServices: TestServices) : AfterAnalysisChecker(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(AkkiDirectives)

    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::SourcelessDiagnostics))

    override fun check(thereWereFailures: Boolean) {
        val expected = testServices.moduleStructure.originalTestDataFiles.first().withExtension("sourceless.txt")
        if (AkkiDirectives.CHECK_SOURCELESS_DIAGNOSTICS !in testServices.moduleStructure.allDirectives) {
            testServices.assertions.assertFileDoesntExist(expected, AkkiDirectives.CHECK_SOURCELESS_DIAGNOSTICS)
            return
        }
        testServices.assertions.assertEqualsToFile(expected, testServices.sourcelessDiagnostics.dump())
    }
}
