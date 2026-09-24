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
import org.jetbrains.kotlin.psi.KtExpression

internal object AkkiErrors : KtDiagnosticsContainer() {
    val LOG_AS_INITIALIZER by DiagnosticFactory2DelegateProvider<String, String>(
        Severity.INFO,
        SourceElementPositioningStrategies.DEFAULT,
        KtExpression::class,
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
            AkkiErrors.LOG_AS_INITIALIZER,
            "''{0}'' is initialized with ''{1}''. Use ''logger()'' to store the logger of the enclosing declaration.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.LOGGING_CALL_REFERENCE,
            "Callable references to ''{0}'' are not supported. They bypass the compiler plugin, report Akki as " +
                "the caller, and ignore ''minLevel''. Use a lambda instead.",
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.CONTEXTUAL_LOGGER_REFERENCE,
            "Callable references to ''{0}'' are not supported. The plugin cannot derive a logger name through " +
                "a reference, and invoking it fails at runtime. Call ''{0}'' directly or use ''Log.of<T>()'' " +
                "or ''Log.named(\"...\")''.",
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.LOGGING_CALL_REMOVED,
            "The ''{0}'' record is below ''minLevel={1}'' and is removed at compile time.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            AkkiErrors.BLANK_LOG_NAME,
            "'Log.named' requires a non-blank logger name.",
        )
        map.put(AkkiErrors.INCOMPATIBLE_AKKI_CORE, MESSAGE_PLACEHOLDER)
    }
}
