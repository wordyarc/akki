package fixture

import io.akki.*

fun box(): String {
    val logger = Log.named("fixture")
    val write: (String, Throwable?, Map<String, Any?>) -> Unit = logger::debug
    write("via-reference", null, emptyMap())
    return logger.name
}
