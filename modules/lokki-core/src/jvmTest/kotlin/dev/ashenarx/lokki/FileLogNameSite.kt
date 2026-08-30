@file:LogName("file-audit")

package dev.ashenarx.lokki

internal fun fileLogger(): Logger = log

internal fun fileLoggerFromFunction(): Logger = logger()

internal fun fileLoggerFromFactory(): Logger = Log.ofCaller()
