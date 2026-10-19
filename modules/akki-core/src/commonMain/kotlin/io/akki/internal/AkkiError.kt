@file:OptIn(InternalAkkiApi::class)

package io.akki.internal

import io.akki.InternalAkkiApi

internal fun akkiError(message: String): Nothing = error("akki: $message")

internal fun akkiError(message: String, cause: Throwable): Nothing = throw IllegalStateException("akki: $message", cause)

internal fun configurationError(message: String): Nothing = throw AkkiConfigurationException("akki: $message")

internal fun configurationError(message: String, cause: Throwable): Nothing =
    throw AkkiConfigurationException("akki: $message", cause)
