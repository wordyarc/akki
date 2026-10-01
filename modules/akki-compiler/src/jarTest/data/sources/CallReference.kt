package sample

import io.akki.Logger

fun callReference(logger: Logger) {
    val write: (String, Throwable?, Map<String, Any?>) -> Unit = logger::info
    write("via-reference", null, emptyMap())
}
