@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import io.akki.compiler.compat.CompilerCompat
import io.akki.compiler.compat.CompilerCompatLoader
import io.akki.compiler.compat.breaksLinkage
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.report
import org.jetbrains.kotlin.cli.reportLog
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration

internal class AkkiCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = AkkiNames.PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        try {
            register(load(configuration), configuration)
        } catch (failure: CompatLoadException) {
            configuration.reportStartFailure(failure)
        }
    }

    private fun load(configuration: CompilerConfiguration): CompilerCompat = try {
        CompilerCompatLoader.load { selection -> configuration.reportLog("$PLUGIN starts. $selection", null) }
    } catch (failure: CompatLoadException) {
        throw if (failure.cause?.breaksLinkage == true) unlinked(failure) else failure
    } catch (failure: LinkageError) {
        if (!failure.breaksLinkage) throw failure
        throw unlinked(failure)
    }

    private fun ExtensionStorage.register(compat: CompilerCompat, configuration: CompilerConfiguration) {
        try {
            registerAkkiExtensions(configuration[AkkiConfigurationKeys.MIN_LEVEL, MinLevel.DEFAULT], compat)
        } catch (failure: LinkageError) {
            if (!failure.breaksLinkage) throw failure
            throw unlinked(failure)
        } catch (failure: ClassCastException) {
            throw unlinked(failure)
        }
    }
}

private fun unlinked(failure: Throwable): CompatLoadException = CompatLoadException(unlinkedCompiler(), failure)

private fun CompilerConfiguration.reportStartFailure(failure: CompatLoadException) {
    val message = "$CANNOT_START ${failure.message}${causeLines(failure.cause)}"
    try {
        report(AkkiErrors.AKKI_CANNOT_START, message, null)
    } catch (unreported: LinkageError) {
        if (!unreported.breaksLinkage) throw unreported
        get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY)?.report(CompilerMessageSeverity.ERROR, message, null)
    }
}
