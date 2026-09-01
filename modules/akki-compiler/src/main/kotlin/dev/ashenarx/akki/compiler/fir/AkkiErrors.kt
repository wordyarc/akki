package dev.ashenarx.akki.compiler.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.psi.KtElement

internal object AkkiErrors : KtDiagnosticsContainer() {
    val LOGGER_LEVEL_METHOD_OVERRIDDEN by warning1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = AkkiDefaultErrorMessages
}

internal object AkkiDefaultErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("Akki") { map ->
        map.put(
            AkkiErrors.LOGGER_LEVEL_METHOD_OVERRIDDEN,
            "Overriding Logger.{0} has no effect: the Akki compiler plugin rewrites such calls to " +
                "Logger.sink and Sink.emit, so this body is skipped at every call site typed as Logger. " +
                "Override emit, isEnabled or sink instead.",
            CommonRenderers.STRING,
        )
    }
}
