@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.InternalAkkiApi
import io.akki.internal.AkkiProperties
import io.akki.internal.LOGGER_NAME_STYLE_PROPERTY
import io.akki.internal.LoggerNameStyle
import io.akki.internal.toEnum

internal val configuredStyle: LoggerNameStyle
    get() = AkkiProperties[LOGGER_NAME_STYLE_PROPERTY].toEnum(LoggerNameStyle.SOURCE)

internal fun <T> byStyle(source: T, jvmClass: T): T = when (configuredStyle) {
    LoggerNameStyle.SOURCE -> source
    LoggerNameStyle.JVM_CLASS -> jvmClass
}
