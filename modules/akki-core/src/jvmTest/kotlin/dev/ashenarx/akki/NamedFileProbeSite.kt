@file:LogName("named-file-probe")

package dev.ashenarx.akki

internal fun namedFileProbe(): JvmLoggerNameProbe = captureLoggerNames("@LogName file", log)
