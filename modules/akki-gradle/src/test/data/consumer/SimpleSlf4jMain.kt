package consumer

import io.akki.log

class OrderService {
    fun handle() {
        var evaluated = 0
        log.trace { evaluated++; "trace message" }
        log.debug { evaluated++; "debug message" }
        log.info("received A-1", fields = linkedMapOf("order" to "A-1", "tenant" to null))
        log.warn("warn message")
        log.error("failed", IllegalStateException("boom"))
        println("AKKI evaluated=$evaluated")
    }
}

fun main() {
    OrderService().handle()
}
