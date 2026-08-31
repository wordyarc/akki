@file:JvmName("SharedProbeFacade")
@file:JvmMultifileClass
@file:LogName("named-multifile-probe")

package dev.ashenarx.akki

internal fun namedMultifileProbe(): JvmLoggerNameProbe = captureLoggerNames("@LogName multifile", log)
