package dev.ashenarx.akki.internal

import dev.ashenarx.akki.Level
import dev.ashenarx.akki.LogBackend
import dev.ashenarx.akki.Sink

internal object DefaultBackend : LogBackend {
    private const val NOTICE: String =
        "akki: no backend installed, writing to stderr at INFO. Install one with Log.install(backend)."

    internal var noticed: Boolean = false

    override fun isEnabled(name: String, level: Level): Boolean = level >= Level.INFO

    override fun resolve(name: String, level: Level): Sink? {
        if (level < Level.INFO) return null
        return Sink { message, cause, fields ->
            if (!noticed) {
                noticed = true
                printError(NOTICE)
            }
            printError(format(level, name, message, fields))
            cause?.let { printError(it.stackTraceToString().trimEnd()) }
        }
    }

    private fun format(level: Level, name: String, message: String, fields: Map<String, Any?>): String =
        buildString {
            append(level.name.padEnd(5))
            append(' ')
            append(name)
            append(" - ")
            append(message)
            if (fields.isNotEmpty()) {
                fields.entries.joinTo(this, prefix = " {", postfix = "}") { (key, value) -> "$key=$value" }
            }
        }
}
