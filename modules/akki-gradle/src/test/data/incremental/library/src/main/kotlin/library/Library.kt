@file:Suppress("NOTHING_TO_INLINE")

package library

import io.akki.Log
import io.akki.Logger
import io.akki.log

inline fun traced(): String {
    log.debug { "debug one" }
    log.info("info one")
    return log.name + ":one"
}

abstract class Handler {
    val logger: Logger = Log.of(javaClass)
}

inline fun capturing(crossinline block: () -> String): Handler = object : Handler() {
    override fun toString(): String = block() + ":one"
}
