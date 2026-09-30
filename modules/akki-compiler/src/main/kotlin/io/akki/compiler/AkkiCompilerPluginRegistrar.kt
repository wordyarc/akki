@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import io.akki.compiler.compat.CompilerCompatLoader
import io.akki.compiler.fir.AkkiFirExtensionRegistrar
import io.akki.compiler.ir.AkkiIrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration

internal class AkkiCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = AkkiNames.PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messages = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY)
        val compat = try {
            CompilerCompatLoader.load { selection ->
                messages?.report(CompilerMessageSeverity.LOGGING, "$PLUGIN starts. $selection", null)
            }
        } catch (failure: CompatLoadException) {
            messages.reportStartFailure(failure)
            return
        }
        compat.registerExtensions(
            storage = this,
            fir = AkkiFirExtensionRegistrar(),
            ir = AkkiIrGenerationExtension(
                minLevel = configuration[MIN_LEVEL, MinLevel.DEFAULT],
                compat = compat,
            ),
        )
    }
}

private const val PLUGIN: String = "akki: compiler plugin $AKKI_VERSION"

private fun MessageCollector?.reportStartFailure(failure: CompatLoadException) {
    if (this == null || this === MessageCollector.NONE) throw failure
    val causes = generateSequence(failure.cause, Throwable::cause).joinToString("") { "\nCaused by: $it" }
    report(CompilerMessageSeverity.ERROR, "$PLUGIN cannot start. ${failure.message}$causes", null)
}
