package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.IrStatementOriginImpl

internal data object LoggerField : GeneratedDeclarationKey()

internal val LOGGER_FIELD: IrDeclarationOrigin = IrDeclarationOrigin.GeneratedByPlugin(LoggerField)

internal val SINK_GUARD by IrStatementOriginImpl
