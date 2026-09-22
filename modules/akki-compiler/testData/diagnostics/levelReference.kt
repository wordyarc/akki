// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*

fun probe(): String {
    val logger = Log.named("fixture")
    val write: (String, Throwable?, Map<String, Any?>) -> Unit = <!LOGGING_CALL_REFERENCE!>logger::debug<!>
    write("via-reference", null, emptyMap())
    return logger.name
}
