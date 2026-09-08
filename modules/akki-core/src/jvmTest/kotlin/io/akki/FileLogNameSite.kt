@file:LogName("file-audit")

package io.akki

internal fun fileLogger(): Logger = log

internal fun fileLoggerFromFunction(): Logger = logger()

internal fun fileLoggerFromFactory(): Logger = Log.forCaller()
