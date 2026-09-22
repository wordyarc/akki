// RUN_PIPELINE_TILL: BACKEND
// WARNING_LEVEL: LOGGING_CALL_REFERENCE:disabled
// CHECK_SOURCELESS_DIAGNOSTICS
package fixture

import io.akki.*

fun probe() {
    val logger = Log.named("fixture")
    val write: (String, Throwable?, Map<String, Any?>) -> Unit = logger::debug
    write("via-reference", null, emptyMap())
}
