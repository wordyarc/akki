package io.akki

import io.akki.internal.JvmLoggerNameStyle
import io.akki.internal.parseJvmLoggerNameStyle

internal val configuredStyle: JvmLoggerNameStyle
    get() = parseJvmLoggerNameStyle(System.getProperty(LOGGER_NAME_STYLE_PROPERTY_NAME))

internal fun <T> byStyle(source: T, jvmClass: T): T = when (configuredStyle) {
    JvmLoggerNameStyle.SOURCE -> source
    JvmLoggerNameStyle.JVM_CLASS -> jvmClass
}
