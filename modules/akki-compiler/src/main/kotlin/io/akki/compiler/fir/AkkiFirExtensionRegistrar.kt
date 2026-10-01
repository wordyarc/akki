package io.akki.compiler.fir

import io.akki.compiler.AkkiErrors
import io.akki.compiler.linked
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

internal class AkkiFirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val callableReferenceAccessCheckers:
            Set<FirExpressionChecker<FirCallableReferenceAccess>> =
            setOf(LevelReferenceChecker, ContextualLoggerReferenceChecker)

        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(LoggerNameChecker)
    }

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val propertyCheckers: Set<FirPropertyChecker> = setOf(LogInitializerChecker)
    }
}

internal class AkkiFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        linked {
            +::linkedCheckers
            registerDiagnosticContainers(AkkiErrors)
        }
    }
}

private fun linkedCheckers(session: FirSession): FirAdditionalCheckersExtension = linked { AkkiFirCheckers(session) }
