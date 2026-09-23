package io.akki.compiler.fir

import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression

internal fun FirEvaluatorResult?.stringValue(): String? =
    ((this as? FirEvaluatorResult.Evaluated)?.result as? FirLiteralExpression)?.value as? String
