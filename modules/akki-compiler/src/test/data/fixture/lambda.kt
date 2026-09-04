package fixture

import dev.ashenarx.akki.*
import dev.ashenarx.akki.test.*

@OptIn(DelicateAkkiApi::class)
fun box(): String {
    val backend = RecordingBackend()
    var counter = 0
    withBackend(backend) {
        Log.named("fixture").info { "value=${++counter}" }
    }
    return backend.records.messages.joinToString(",")
}
