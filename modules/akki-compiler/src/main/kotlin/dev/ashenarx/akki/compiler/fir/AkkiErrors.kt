package dev.ashenarx.akki.compiler.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.warning2
import org.jetbrains.kotlin.psi.KtElement

internal object AkkiErrors : KtDiagnosticsContainer() {
    val CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION by warning2<KtElement, String, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = AkkiDefaultErrorMessages
}

internal object AkkiDefaultErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("Akki") { map ->
        map.put(
            AkkiErrors.CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION,
            "''{0}'' inside the inline declaration ''{1}'' is resolved against the caller, not against ''{1}'': " +
                "the body is inlined, so every call site gets a logger named after itself. " +
                "The Akki compiler plugin leaves this lookup to the runtime fallback so that both agree. " +
                "Declare an explicit logger if the name of this declaration is what you want.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
    }
}
