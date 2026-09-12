package io.akki.compiler.fir

import io.akki.compiler.AkkiErrors
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.utils.visibility

internal object RedundantLoggerPropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        if (declaration.isVar || declaration.delegate != null) return
        if (!declaration.isShadowable()) return
        if (context.enclosingInlineFunction() != null) return
        val callee = declaration.initializer?.callSiteName(context.session) ?: return
        reporter.reportOn(
            declaration.source,
            AkkiErrors.REDUNDANT_LOGGER_PROPERTY,
            declaration.name.asString(),
            callee.asString(),
        )
    }

    private fun FirProperty.isShadowable(): Boolean =
        (isLocal || visibility == Visibilities.Private) &&
            (getter == null || getter is FirDefaultPropertyGetter)
}
