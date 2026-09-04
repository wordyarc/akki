package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStatementOriginImpl

internal val GENERATED_LOGGER_FIELD: IrDeclarationOrigin by IrDeclarationOriginImpl.Synthetic

internal val AKKI_RECORD: IrStatementOrigin by IrStatementOriginImpl
