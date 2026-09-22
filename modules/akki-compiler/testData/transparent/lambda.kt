// CHECK_BYTECODE_TEXT
// 0 kotlin/jvm/functions/Function0
// 0 box\$lambda
package fixture

import io.akki.*
import io.akki.test.*
import kotlin.test.assertEquals

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend(Level.INFO)
    var counter = 0
    withBackend(backend) {
        Log.named("fixture").info { "value=${++counter}" }
        Log.named("fixture").debug { "never=${++counter}" }
    }
    assertEquals("value=1", backend.records.map { it.message }.joinToString(","))
    assertEquals(1, counter)
    return "OK"
}
