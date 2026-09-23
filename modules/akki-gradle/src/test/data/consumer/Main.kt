package consumer

import io.akki.*
import io.akki.backend.*

class OrderService {
    fun handle(): String = log.name

    fun eager(counter: () -> Int): Unit = log.debug("value=${counter()}")

    fun lazy(counter: () -> Int): Unit = log.debug { "value=${counter()}" }
}

@OptIn(DelicateAkkiApi::class)
fun main() {
    var eager = 0
    var lazy = 0
    val backend = LogBackend { _ ->
        LoggerBinding { level -> if (level == Level.DEBUG) null else Sink { _, _, _ -> } }
    }
    val service = OrderService()
    Log.install(backend).use {
        service.eager { ++eager }
        service.lazy { ++lazy }
    }
    println("AKKI name=${service.handle()} eager=$eager lazy=$lazy")
}
