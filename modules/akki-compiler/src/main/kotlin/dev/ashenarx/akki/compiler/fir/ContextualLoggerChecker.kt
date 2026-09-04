package dev.ashenarx.akki.compiler.fir

import dev.ashenarx.akki.compiler.AkkiErrors
import dev.ashenarx.akki.compiler.AkkiNames
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.name.Name

internal object ContextualLoggerChecker :
    FirExpressionChecker<FirQualifiedAccessExpression>(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirQualifiedAccessExpression) {
        val callee = expression.calleeReference.toResolvedCallableSymbol() ?: return
        if (!callee.hasAnnotation(AkkiNames.CALL_SITE_ID, context.session)) return
        val inlined = context.inlineFunctionBodyContext?.inlineFunction ?: return
        reporter.reportOn(
            expression.source,
            AkkiErrors.CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION,
            callee.name.asString(),
            inlined.declarationName().asString(),
        )
    }

    private fun FirFunction.declarationName(): Name =
        (this as? FirPropertyAccessor)?.propertySymbol?.name ?: symbol.name
}
