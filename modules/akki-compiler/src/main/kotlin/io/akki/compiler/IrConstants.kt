@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.akki.compiler

import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI

internal fun IrExpression.stringConstant(): String? = when (this) {
    is IrConst -> value as? String
    is IrCall -> symbol.owner.correspondingPropertySymbol?.owner?.constantString()
    is IrGetField -> symbol.owner.correspondingPropertySymbol?.owner?.constantString()
    else -> null
}

internal fun IrProperty.constantString(): String? =
    takeIf { isConst }?.backingField?.initializer?.expression?.let { (it as? IrConst)?.value as? String }
