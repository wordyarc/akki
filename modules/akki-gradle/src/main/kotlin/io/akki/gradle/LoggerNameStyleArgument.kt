package io.akki.gradle

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions

internal fun JavaForkOptions.passLoggerNameStyle(providers: ProviderFactory, style: Provider<LoggerNameStyle>) {
    val explicit = providers.provider { setsLoggerNameStyle() }
    jvmArgumentProviders.add(LoggerNameStyleArgument(style, explicit))
}

private fun JavaForkOptions.setsLoggerNameStyle(): Boolean =
    LOGGER_NAME_STYLE_PROPERTY in systemProperties ||
        jvmArgs.orEmpty().any { it.substringBefore('=') == "-D$LOGGER_NAME_STYLE_PROPERTY" }

internal class LoggerNameStyleArgument(
    @get:Input @get:Optional val style: Provider<LoggerNameStyle>,
    @get:Input val explicit: Provider<Boolean>,
) : CommandLineArgumentProvider {
    override fun asArguments(): List<String> =
        if (explicit.get()) emptyList() else listOfNotNull(style.orNull?.argument)
}

private val LoggerNameStyle.argument: String
    get() = "-D$LOGGER_NAME_STYLE_PROPERTY=${name.lowercase().replace('_', '-')}"

private const val LOGGER_NAME_STYLE_PROPERTY: String = "io.akki.loggerNameStyle"
