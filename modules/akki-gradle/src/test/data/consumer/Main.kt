package consumer

import io.akki.*
import io.akki.backend.*

class OrderService {
    fun handle(): String = log.name

    fun suppressed(counter: () -> Int): Unit = log.debug("value=${counter()}")
}

@OptIn(DelicateAkkiApi::class)
fun main() {
    var evaluated = 0
    val backend = LogBackend { _, level ->
        if (level == Level.DEBUG) null else Sink { _, _, _ -> }
    }
    val service = OrderService()
    Log.install(backend).use {
        service.suppressed { ++evaluated }
    }
    println("AKKI name=${service.handle()} evaluated=$evaluated")
}
