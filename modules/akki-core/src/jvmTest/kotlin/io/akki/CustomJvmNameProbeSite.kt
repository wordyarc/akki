@file:JvmName("CustomProbeFacade")

package io.akki

internal fun customJvmNameProbe(): JvmLoggerNameProbe = captureLoggerNames("@file:JvmName")

internal fun customJvmNameLocalLoggers(): Logger {
    class Local

    return Log.of(Local::class.java)
}
