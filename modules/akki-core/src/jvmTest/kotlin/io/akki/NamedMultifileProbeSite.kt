@file:JvmName("SharedProbeFacade")
@file:JvmMultifileClass
@file:LogName("named-multifile-probe")

package io.akki

internal fun namedMultifileProbe(): JvmLoggerNameProbe = captureLoggerNames("@LogName multifile")
