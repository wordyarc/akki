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
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.psi.KtElement

internal object AkkiErrors : KtDiagnosticsContainer() {
    val CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION by error2<KtElement, String, String>()

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
            AkkiErrors.CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION,
            "''{0}'' is not available inside the inline declaration ''{1}'': the body is inlined into every " +
                "call site, so a generated logger field is read from another module and the plugin cannot " +
                "lower it. Use ''Log.of<T>()'' or ''Log.named(\"...\")'' here.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.REDUNDANT_LOGGER_PROPERTY,
            "''{0}'' holds the logger of the enclosing declaration, which is what ''{1}'' resolves to anywhere " +
                "in it. The Akki compiler plugin already keeps that logger in a static field, so the property " +
                "only adds another one. Use ''Log.of(javaClass)'' for the logger of the runtime type.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.LOGGING_CALL_REFERENCE,
            "''{0}'' cannot be taken as a callable reference: a reference is not lowered, so the record skips the " +
                "level check, reports Akki as the caller and survives the ''minLevel'' threshold. Wrap the call in " +
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
            "This ''{0}'' record is removed at compile time: the Akki compiler plugin runs with " +
                "minLevel={1}. The call, its arguments and its message are not in the bytecode, so no logging " +
                "configuration can bring the record back. Lower ''minLevel'' to keep it.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.BLANK_LOG_NAME,
            "'@LogName' must name the logger of this declaration, but the name is blank. Remove the annotation " +
                "to keep the name derived from the declaration, or give it a non-blank name.",
        )
        map.put(AkkiErrors.INCOMPATIBLE_AKKI_CORE, MESSAGE_PLACEHOLDER)
    }
}
