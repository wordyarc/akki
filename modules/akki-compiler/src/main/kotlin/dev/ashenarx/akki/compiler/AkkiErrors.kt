package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticFactory2DelegateProvider
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.errorWithoutSource
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.BaseSourcelessDiagnosticRendererFactory.Companion.MESSAGE_PLACEHOLDER
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.warning2
import org.jetbrains.kotlin.psi.KtElement

internal object AkkiErrors : KtDiagnosticsContainer() {
    val CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION by warning2<KtElement, String, String>()

    val REDUNDANT_LOGGER_PROPERTY by DiagnosticFactory2DelegateProvider<String, String>(
        Severity.INFO,
        SourceElementPositioningStrategies.DECLARATION_NAME,
        KtElement::class,
        this,
    )

    val INCOMPATIBLE_AKKI_CORE by errorWithoutSource()

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
        map.put(
            AkkiErrors.REDUNDANT_LOGGER_PROPERTY,
            "''{0}'' holds the logger of the enclosing declaration, which is what ''{1}'' resolves to anywhere " +
                "in it. The Akki compiler plugin already keeps that logger in a static field, so the property " +
                "only adds another one. Use ''Log.of(javaClass)'' if you meant the logger of the runtime type.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(AkkiErrors.INCOMPATIBLE_AKKI_CORE, MESSAGE_PLACEHOLDER)
    }
}
