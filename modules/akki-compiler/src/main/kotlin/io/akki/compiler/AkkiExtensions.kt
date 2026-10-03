@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler

import io.akki.compiler.compat.CompilerCompat
import io.akki.compiler.fir.AkkiFirExtensionRegistrar
import io.akki.compiler.ir.AkkiIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

internal fun CompilerPluginRegistrar.ExtensionStorage.registerAkkiExtensions(minLevel: MinLevel, compat: CompilerCompat) {
    FirExtensionRegistrarAdapter.registerExtension(AkkiFirExtensionRegistrar())
    IrGenerationExtension.registerExtension(AkkiIrGenerationExtension(minLevel, compat))
}
