package io.akki.internal

internal fun akkiError(message: String): Nothing = error("akki: $message")

internal fun akkiError(message: String, cause: Throwable): Nothing = throw IllegalStateException("akki: $message", cause)
