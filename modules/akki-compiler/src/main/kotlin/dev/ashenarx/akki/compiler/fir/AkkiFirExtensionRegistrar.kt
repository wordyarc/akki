package dev.ashenarx.akki.compiler.fir

import dev.ashenarx.akki.compiler.AkkiErrors
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

internal class AkkiFirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val qualifiedAccessExpressionCheckers:
            Set<FirExpressionChecker<FirQualifiedAccessExpression>> = setOf(ContextualLoggerChecker)
    }
}

internal class AkkiFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::AkkiFirCheckers
        registerDiagnosticContainers(AkkiErrors)
    }
}
