package dev.ashenarx.akki.compiler.fir

import dev.ashenarx.akki.compiler.AkkiNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.name.Name

internal fun FirExpression.callSiteName(session: FirSession): Name? =
    (this as? FirQualifiedAccessExpression)
        ?.calleeReference
        ?.toResolvedCallableSymbol()
        ?.takeIf { it.hasAnnotation(AkkiNames.CALL_SITE_ID, session) }
        ?.name
