package io.akki.compiler.fir

import io.akki.compiler.AkkiNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.isSubpackageOf

internal fun FirExpression.callSiteName(session: FirSession): Name? {
    val symbol = (this as? FirQualifiedAccessExpression)?.calleeReference?.toResolvedCallableSymbol() ?: return null
    val packageName = symbol.callableId?.packageName ?: return null
    if (!packageName.isSubpackageOf(AkkiNames.PACKAGE)) return null
    return symbol.name.takeIf { symbol.hasAnnotation(AkkiNames.CALL_SITE_ID, session) }
}
