package io.akki.compiler.fir

import io.akki.compiler.AkkiNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.name.Name

internal fun FirExpression.callSiteName(session: FirSession): Name? =
    (this as? FirQualifiedAccessExpression)
        ?.calleeReference
        ?.toResolvedCallableSymbol()
        ?.takeIf { it.hasAnnotation(AkkiNames.CALL_SITE_ID, session) }
        ?.name

internal fun CheckerContext.enclosingInlineFunction(): FirFunctionSymbol<*>? =
    containingDeclarations.asReversed()
        .filterIsInstance<FirFunctionSymbol<*>>()
        .firstOrNull { it.isInline }
