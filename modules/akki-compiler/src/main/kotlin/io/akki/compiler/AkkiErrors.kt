package io.akki.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticFactory2DelegateProvider
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.errorWithoutSource
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.BaseSourcelessDiagnosticRendererFactory.Companion.MESSAGE_PLACEHOLDER
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.psi.KtElement

internal object AkkiErrors : KtDiagnosticsContainer() {
    val REDUNDANT_LOGGER_PROPERTY by DiagnosticFactory2DelegateProvider<String, String>(
        Severity.INFO,
        SourceElementPositioningStrategies.DECLARATION_NAME,
        KtElement::class,
        this,
    )

    val LOGGING_CALL_REFERENCE by error1<KtElement, String>()

    val CONTEXTUAL_LOGGER_REFERENCE by error1<KtElement, String>()

    val LOGGING_CALL_REMOVED by DiagnosticFactory2DelegateProvider<String, String>(
        Severity.INFO,
        SourceElementPositioningStrategies.DEFAULT,
        KtElement::class,
        this,
    )

    val BLANK_LOG_NAME by error0<KtElement>()

    val INCOMPATIBLE_AKKI_CORE by errorWithoutSource()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = AkkiDefaultErrorMessages
}

internal object AkkiDefaultErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("Akki") { map ->
        map.put(
            AkkiErrors.REDUNDANT_LOGGER_PROPERTY,
            "''{0}'' stores the logger that ''{1}'' already resolves here; nested declarations with their own " +
                "qualified name resolve a different one.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.LOGGING_CALL_REFERENCE,
            "''{0}'' cannot be taken as a callable reference: a reference is not lowered, so the record " +
                "reports Akki as the caller and survives the ''minLevel'' threshold. Wrap the call in " +
                "a lambda instead.",
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.CONTEXTUAL_LOGGER_REFERENCE,
            "''{0}'' cannot be taken as a callable reference: a reference is not lowered, so invoking it has no " +
                "declaration to name the logger after and fails at runtime as if the plugin were absent. Call " +
                "''{0}'' directly, or use ''Log.of<T>()'' or ''Log.named(\"...\")''.",
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.LOGGING_CALL_REMOVED,
            "This ''{0}'' record is removed at compile time by ''minLevel={1}''.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.BLANK_LOG_NAME,
            "'Log.named' is given a blank logger name, which no backend can route or filter. Use a non-blank name.",
        )
        map.put(AkkiErrors.INCOMPATIBLE_AKKI_CORE, MESSAGE_PLACEHOLDER)
    }
}
