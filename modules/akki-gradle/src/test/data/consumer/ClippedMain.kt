package consumer

import io.akki.*
import io.akki.backend.*

class OrderService {
    fun handle(id: String) {
        log.debug("debug $id")
        log.info("info $id")
    }
}

@OptIn(DelicateAkkiApi::class)
fun main() {
    val records = mutableListOf<String>()
    val backend = LogBackend { _, _ -> Sink { message, _, _ -> records += message } }
    Log.install(backend).use { OrderService().handle("A-1") }
    println("AKKI records=${records.joinToString(",")}")
}
