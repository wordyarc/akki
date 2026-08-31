@file:JvmName("CustomProbeFacade")

package dev.ashenarx.akki

internal fun customJvmNameProbe(): JvmLoggerNameProbe = captureLoggerNames("@file:JvmName", log)

internal fun customJvmNameLocalLoggers(): Pair<Logger, Logger> {
    class Local {
        fun contextual(): Logger = log
    }

    val local = Local()
    return local.contextual() to Log.of(local.javaClass)
}
