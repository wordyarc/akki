package io.akki

import io.akki.internal.JvmLoggerNameStyle
import io.akki.internal.parseJvmLoggerNameStyle

internal val configuredStyle: JvmLoggerNameStyle
    get() = parseJvmLoggerNameStyle(System.getProperty("io.akki.loggerNameStyle"))

internal fun <T> byStyle(source: T, jvmClass: T): T = when (configuredStyle) {
    JvmLoggerNameStyle.SOURCE -> source
    JvmLoggerNameStyle.JVM_CLASS -> jvmClass
}
