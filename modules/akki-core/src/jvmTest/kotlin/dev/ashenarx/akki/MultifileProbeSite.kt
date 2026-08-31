@file:JvmName("SharedProbeFacade")
@file:JvmMultifileClass

package dev.ashenarx.akki

internal fun multifileProbe(): JvmLoggerNameProbe = captureLoggerNames("multifile", log)

internal fun multifileLocalLoggers(): Pair<Logger, Logger> {
    class Local {
        fun contextual(): Logger = log
    }

    val local = Local()
    return local.contextual() to Log.of(local.javaClass)
}
