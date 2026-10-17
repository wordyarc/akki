package io.akki.internal

import io.akki.Logger

internal fun getLogger(name: String): Logger {
    require(name.isNotBlank()) { "akki: a logger name must not be blank: every backend routes and filters on it" }
    return getOrCreateLogger(name)
}
