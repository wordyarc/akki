package sample

import io.akki.DelicateAkkiApi
import io.akki.Log
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.log

class Service {
    fun handle(order: String) {
        log.debug { "debug $order" }
        log.info { "handled $order" }
    }
}

@OptIn(DelicateAkkiApi::class)
fun main() {
    val records = mutableListOf<String>()
    val backend = LogBackend { name ->
        LoggerBinding { level -> Sink { message, _, _ -> records += "$level $name $message" } }
    }
    Log.install(backend).use { Service().handle("A-1") }
    println("AKKI records=$records")
}
