package fixture

import io.akki.*
import io.akki.test.*

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend(Level.INFO)
    var counter = 0
    withBackend(backend) {
        Log.named("fixture").info { "value=${++counter}" }
        Log.named("fixture").debug { "never=${++counter}" }
    }
    return backend.records.map { it.message }.joinToString(",") + "|counter=$counter"
}
