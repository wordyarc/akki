@file:LogName("named-file-probe")

package io.akki

internal fun namedFileProbe(): JvmLoggerNameProbe = captureLoggerNames("@LogName file", log)
