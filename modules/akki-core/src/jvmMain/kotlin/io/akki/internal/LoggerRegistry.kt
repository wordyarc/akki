package io.akki.internal

import io.akki.Logger
import java.util.concurrent.ConcurrentHashMap

private val loggers: ConcurrentHashMap<String, Logger> = ConcurrentHashMap()

internal actual fun platformLogger(name: String): Logger = loggers.computeIfAbsent(name, ::LoggerImpl)
