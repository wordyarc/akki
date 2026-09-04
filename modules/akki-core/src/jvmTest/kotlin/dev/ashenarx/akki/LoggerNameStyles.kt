package dev.ashenarx.akki

import dev.ashenarx.akki.internal.JvmLoggerNameStyle
import dev.ashenarx.akki.internal.parseJvmLoggerNameStyle

internal val configuredStyle: JvmLoggerNameStyle
    get() = parseJvmLoggerNameStyle(System.getProperty("dev.ashenarx.akki.loggerNameStyle"))

internal fun <T> byStyle(source: T, jvmClass: T): T = when (configuredStyle) {
    JvmLoggerNameStyle.SOURCE -> source
    JvmLoggerNameStyle.JVM_CLASS -> jvmClass
}
