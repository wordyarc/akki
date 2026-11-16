package io.akki.compiler.fir

import io.akki.compiler.AkkiErrors
import io.akki.compiler.AkkiNames
import io.akki.compiler.linked
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedNamedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.arrayElementType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.isSubtypeOf
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.resolvedType

internal object RecordFieldShadowingChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        linked {
            val name = expression.calleeReference.name
            if (name !in AkkiNames.RECORD_FUNCTIONS || expression.explicitReceiver != null) return
            val callee = expression.calleeReference.toResolvedNamedFunctionSymbol() ?: return
            val target = callee.callableId
            if (target.classId == null && target.packageName == AkkiNames.PACKAGE) return
            if (!callee.resolvedReturnType.isUnit && !context.isStatement()) return
            val session = context.session
            if (context.containingDeclarations.none { it.takesRecordScope(session) }) return
            val arguments = expression.passedArguments()
            val shadowed = session.symbolProvider.getTopLevelFunctionSymbols(AkkiNames.PACKAGE, name)
            if (shadowed.none { it.accepts(arguments, session) }) return
            reporter.reportOn(
                expression.calleeReference.source,
                AkkiErrors.RECORD_FIELD_SHADOWED,
                name.asString(),
                target.asSingleFqName().asString(),
            )
        }
    }

    private fun CheckerContext.isStatement(): Boolean =
        containingElements.run { getOrNull(lastIndex - 1) } is FirBlock

    private fun FirBasedSymbol<*>.takesRecordScope(session: FirSession): Boolean =
        this is FirCallableSymbol<*> && contextParameterSymbols.any {
            it.resolvedReturnType.fullyExpandedType(session).classId == AkkiNames.RECORD_SCOPE_ID
        }

    private fun FirFunctionCall.passedArguments(): List<FirExpression> =
        arguments.flatMap { (it as? FirVarargArgumentsExpression)?.arguments ?: listOf(it) }
            .map(FirExpression::unwrapArgument)

    private fun FirNamedFunctionSymbol.accepts(arguments: List<FirExpression>, session: FirSession): Boolean {
        val parameters = valueParameterSymbols
        val vararg = parameters.singleOrNull()?.takeIf { it.isVararg }
        if (vararg != null) {
            val element = vararg.resolvedReturnType.arrayElementType() ?: return false
            return arguments.isNotEmpty() && arguments.all { it.resolvedType.isSubtypeOf(element, session) }
        }
        return arguments.size == parameters.size && arguments.indices.all { index ->
            arguments[index].resolvedType.isSubtypeOf(parameters[index].resolvedReturnType, session)
        }
    }
}
