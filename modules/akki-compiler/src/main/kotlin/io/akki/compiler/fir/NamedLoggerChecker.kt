@file:OptIn(PrivateConstantEvaluatorAPI::class)

package io.akki.compiler.fir

import io.akki.compiler.AkkiErrors
import io.akki.compiler.AkkiNames
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirExpressionEvaluator
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.PrivateConstantEvaluatorAPI
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol

internal object NamedLoggerChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        if (expression.calleeReference.toResolvedCallableSymbol()?.callableId != AkkiNames.NAMED_ID) return
        val argument = expression.arguments.singleOrNull()?.unwrapArgument() ?: return
        val name = FirExpressionEvaluator.evaluateExpression(argument, context.session).stringValue() ?: return
        if (name.isNotBlank()) return
        reporter.reportOn(argument.source, AkkiErrors.BLANK_LOG_NAME, "Log.named")
    }
}
