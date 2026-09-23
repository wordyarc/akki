package io.akki.internal

internal fun akkiError(message: String): Nothing = error("akki: $message")
