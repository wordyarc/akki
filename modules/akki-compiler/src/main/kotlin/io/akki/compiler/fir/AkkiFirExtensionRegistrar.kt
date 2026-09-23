package io.akki.compiler.fir

import io.akki.compiler.AkkiErrors
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

internal class AkkiFirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val qualifiedAccessExpressionCheckers:
            Set<FirExpressionChecker<FirQualifiedAccessExpression>> = setOf(ContextualLoggerChecker)

        override val callableReferenceAccessCheckers:
            Set<FirExpressionChecker<FirCallableReferenceAccess>> =
            setOf(LevelReferenceChecker, ContextualLoggerReferenceChecker)

        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(NamedLoggerChecker)
    }

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val propertyCheckers: Set<FirPropertyChecker> = setOf(RedundantLoggerPropertyChecker)
    }
}

internal class AkkiFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::AkkiFirCheckers
        registerDiagnosticContainers(AkkiErrors)
    }
}
