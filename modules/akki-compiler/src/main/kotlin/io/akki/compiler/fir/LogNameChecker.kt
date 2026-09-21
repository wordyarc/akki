package io.akki.compiler.fir

import io.akki.compiler.AkkiErrors
import io.akki.compiler.AkkiNames
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirAnnotationChecker
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpressionEvaluator
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression

internal object LogNameChecker : FirAnnotationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirAnnotation) {
        if (expression.toAnnotationClassId(context.session) != AkkiNames.LOG_NAME_ID) return
        val evaluated = FirExpressionEvaluator.evaluateAnnotationArguments(expression, context.session)[AkkiNames.VALUE]
        val literal = (evaluated as? FirEvaluatorResult.Evaluated)?.result as? FirLiteralExpression ?: return
        val name = literal.value as? String ?: return
        if (name.isNotBlank()) return
        val argument = expression.argumentMapping.mapping[AkkiNames.VALUE]
        reporter.reportOn(argument?.source ?: expression.source, AkkiErrors.BLANK_LOG_NAME)
    }
}
