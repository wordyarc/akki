package io.akki.compiler.fir

import io.akki.compiler.AkkiErrors
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.name.Name

internal object ContextualLoggerChecker :
    FirExpressionChecker<FirQualifiedAccessExpression>(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirQualifiedAccessExpression) {
        if (expression is FirCallableReferenceAccess) return
        val callee = expression.callSiteName(context.session) ?: return
        val inlined = context.enclosingInlineFunction() ?: return
        reporter.reportOn(
            expression.source,
            AkkiErrors.CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION,
            callee.asString(),
            inlined.declarationName().asString(),
        )
    }

    private fun FirFunctionSymbol<*>.declarationName(): Name =
        (this as? FirPropertyAccessorSymbol)?.propertySymbol?.name ?: name
}
