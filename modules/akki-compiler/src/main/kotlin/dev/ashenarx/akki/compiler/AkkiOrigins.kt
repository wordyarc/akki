package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.expressions.IrStatementOriginImpl

internal val LOGGER_FIELD: IrDeclarationOrigin by IrDeclarationOriginImpl.Synthetic

internal val SINK_GUARD by IrStatementOriginImpl
