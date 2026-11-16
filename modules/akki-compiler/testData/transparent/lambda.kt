// CHECK_BYTECODE_TEXT
// 0 kotlin/jvm/functions/Function
// 0 box\$lambda
@file:OptIn(io.akki.InternalAkkiApi::class, io.akki.ExperimentalAkkiApi::class)

package fixture

import io.akki.*
import io.akki.internal.LogScope
import io.akki.internal.withLogScope
import io.akki.test.*
import kotlin.test.assertEquals

fun box(): String {
    val backend = RecordingBackend(Level.INFO)
    var counter = 0
    withLogScope(LogScope(backend)) {
        Log.named("fixture").info { "value=${++counter}" }
        Log.named("fixture").debug { "never=${++counter}" }
        Log.named("fixture").warn { field("counter", ++counter); "with a field" }
        Log.named("fixture").trace { field("counter", ++counter); "never with a field" }
    }
    assertEquals(listOf("value=1", "with a field"), backend.records.map { it.message })
    assertEquals(mapOf("counter" to 2), backend.records.last().fields)
    assertEquals(2, counter)
    return "OK"
}
