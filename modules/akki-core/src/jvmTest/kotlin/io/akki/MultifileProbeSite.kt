@file:JvmName("SharedProbeFacade")
@file:JvmMultifileClass

package io.akki

internal fun multifileProbe(): JvmLoggerNameProbe = captureLoggerNames("multifile")

internal fun multifileLocalLoggers(): Logger {
    class Local

    return Log.of(Local::class.java)
}
