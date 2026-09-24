package io.akki.internal

import io.akki.Logger

internal fun namedLogger(name: String): Logger {
    require(name.isNotBlank()) { "akki: a logger name must not be blank, it is what every backend routes and filters on" }
    return platformLogger(name)
}
