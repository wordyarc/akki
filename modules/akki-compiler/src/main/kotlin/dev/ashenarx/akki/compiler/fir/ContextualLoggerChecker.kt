package dev.ashenarx.akki.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object ContextualLoggerChecker :
    FirExpressionChecker<FirQualifiedAccessExpression>(MppCheckerKind.Common) {
    private val CALL_SITE = ClassId(FqName("dev.ashenarx.akki.internal"), Name.identifier("CallSite"))

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirQualifiedAccessExpression) {
        val callee = expression.calleeReference.toResolvedCallableSymbol() ?: return
        if (!callee.hasAnnotation(CALL_SITE, context.session)) return
        val inlined = context.containingDeclarations
            .filterIsInstance<FirCallableSymbol<*>>()
            .lastOrNull { it.isInline }
            ?: return
        reporter.reportOn(
            expression.source,
            AkkiErrors.CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION,
            callee.name.asString(),
            inlined.declarationName().asString(),
        )
    }

    private fun FirCallableSymbol<*>.declarationName(): Name =
        (this as? FirPropertyAccessorSymbol)?.propertySymbol?.name ?: name
}
