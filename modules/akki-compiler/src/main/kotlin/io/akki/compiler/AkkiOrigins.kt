package io.akki.compiler

import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl

internal val GENERATED_LOGGER_FIELD: IrDeclarationOrigin by IrDeclarationOriginImpl.Synthetic

internal val GENERATED_LOGGER_HOLDER: IrDeclarationOrigin by IrDeclarationOriginImpl.Synthetic
