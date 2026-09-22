package io.akki.compiler.fir

import io.akki.compiler.AkkiErrors
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess

internal object ContextualLoggerReferenceChecker :
    FirExpressionChecker<FirCallableReferenceAccess>(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCallableReferenceAccess) {
        val callee = expression.callSiteName(context.session) ?: return
        reporter.reportOn(expression.source, AkkiErrors.CONTEXTUAL_LOGGER_REFERENCE, callee.asString())
    }
}
