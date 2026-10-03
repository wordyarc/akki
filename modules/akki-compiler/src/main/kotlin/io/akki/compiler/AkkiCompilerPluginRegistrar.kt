@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import io.akki.compiler.compat.CompilerCompat
import io.akki.compiler.compat.CompilerCompatLoader
import io.akki.compiler.compat.breaksLinkage
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
        val messageCollector = configuration.messageCollectorOrNull()
        try {
            register(load(messageCollector), configuration)
        } catch (failure: CompatLoadException) {
            messageCollector.reportStartFailure(failure)
        }
    }

    private fun load(messageCollector: MessageCollector?): CompilerCompat = try {
        CompilerCompatLoader.load { selection ->
            messageCollector?.report(CompilerMessageSeverity.LOGGING, "$PLUGIN starts. $selection", null)
        }
    } catch (failure: CompatLoadException) {
        throw if (failure.cause?.breaksLinkage == true) unlinked(failure) else failure
    }

    private fun ExtensionStorage.register(compat: CompilerCompat, configuration: CompilerConfiguration) {
        try {
            val minLevel = configuration[AkkiConfigurationKeys.MIN_LEVEL, MinLevel.DEFAULT]
            compat.registerExtensions(
                storage = this,
                fir = AkkiFirExtensionRegistrar(),
                ir = AkkiIrGenerationExtension(minLevel, compat),
            )
        } catch (failure: LinkageError) {
            if (!failure.breaksLinkage) throw failure
            throw unlinked(failure)
        } catch (failure: ClassCastException) {
            throw unlinked(failure)
        }
    }
}

private fun unlinked(failure: Throwable): CompatLoadException = CompatLoadException(unlinkedCompiler(), failure)

private fun CompilerConfiguration.messageCollectorOrNull(): MessageCollector? = try {
    get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY)
} catch (failure: LinkageError) {
    if (!failure.breaksLinkage) throw failure
    throw CompatLoadException("$CANNOT_START ${unlinkedCompiler()}", failure)
}

private fun MessageCollector?.reportStartFailure(failure: CompatLoadException) {
    if (this == null) return
    val message = "$CANNOT_START ${failure.message}"
    if (this === MessageCollector.NONE) throw CompatLoadException(message, failure.cause)
    report(CompilerMessageSeverity.ERROR, message + causeLines(failure.cause), null)
}
