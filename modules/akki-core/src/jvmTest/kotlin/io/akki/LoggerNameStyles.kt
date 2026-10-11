package io.akki

import io.akki.internal.LOGGER_NAME_STYLE_PROPERTY
import io.akki.internal.LoggerNameStyle
import io.akki.internal.parseLoggerNameStyle

internal val configuredStyle: LoggerNameStyle
    get() = parseLoggerNameStyle(System.getProperty(LOGGER_NAME_STYLE_PROPERTY))

internal fun <T> byStyle(source: T, jvmClass: T): T = when (configuredStyle) {
    LoggerNameStyle.SOURCE -> source
    LoggerNameStyle.JVM_CLASS -> jvmClass
}
