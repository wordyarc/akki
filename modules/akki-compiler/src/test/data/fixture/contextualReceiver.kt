@file:LogName("receiver-audit")

package fixture

import io.akki.*

private val events = mutableListOf<String>()
private val failure = IllegalStateException("receiver failed")

private fun selectLog(): Log {
    events += "selected"
    return Log
}

private fun throwingLog(): Log {
    events += "throwing"
    throw failure
}

fun box(): String {
    events += "logger=" + selectLog().forCaller().name
    try {
        throwingLog().forCaller()
        events += "not-thrown"
    } catch (caught: IllegalStateException) {
        events += "caught=" + (caught === failure)
    }
    return events.joinToString(",")
}
