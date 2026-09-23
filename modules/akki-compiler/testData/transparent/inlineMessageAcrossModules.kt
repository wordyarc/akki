// MODULE: library
// FILE: Library.kt
package fixture.library

import io.akki.Logger

inline fun <reified T> Logger.typeMessage(crossinline body: () -> String) {
    info { T::class.java.name + body() }
    val action = { info { T::class.java.name + body() } }
    action()
}

// MODULE: main(library)
// FILE: Main.kt
package fixture.consumer

import fixture.library.typeMessage
import io.akki.test.RecordingLogger
import kotlin.test.assertEquals

fun box(): String {
    val logger = RecordingLogger()
    logger.typeMessage<String> { "!" }
    assertEquals(listOf("java.lang.String!", "java.lang.String!"), logger.records.map { it.message })
    return "OK"
}
