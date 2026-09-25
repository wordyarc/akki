package consumer

import io.akki.log
import org.slf4j.LoggerFactory

class OrderService {
    fun handle(id: String) {
        log.info("received $id")
    }
}

fun main() {
    OrderService().handle("A-1")
    LoggerFactory.getLogger("consumer.Application").info("application record")
    println("AKKI slf4j-api=" + LoggerFactory::class.java.`package`.implementationVersion)
}
