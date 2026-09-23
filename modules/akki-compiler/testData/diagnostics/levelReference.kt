// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*
import io.akki.backend.Sink

class Custom : Logger() {
    override val name = "custom"
    override fun sink(level: Level): Sink? = null
}

fun inherited(logger: Custom) {
    val write: (String, Throwable?, Map<String, Any?>) -> Unit = <!LOGGING_CALL_REFERENCE!>logger::info<!>
    write("via-reference", null, emptyMap())
}

fun probe(): String {
    val logger = Log.named("fixture")
    val write: (String, Throwable?, Map<String, Any?>) -> Unit = <!LOGGING_CALL_REFERENCE!>logger::debug<!>
    write("via-reference", null, emptyMap())
    return logger.name
}
