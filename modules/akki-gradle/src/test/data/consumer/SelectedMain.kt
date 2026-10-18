package consumer

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.log

class OrderService {
    fun handle(id: String) {
        log.info("received $id")
    }
}

class DeclaredBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding =
        LoggerBinding { Sink { message, _, _ -> println("AKKI DECLARED $loggerName - $message") } }
}

fun main() {
    OrderService().handle("A-1")
}
