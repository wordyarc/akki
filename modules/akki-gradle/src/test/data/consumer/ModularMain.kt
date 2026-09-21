package consumer

import io.akki.Logger
import io.akki.log

class OrderService {
    fun handle(id: String) {
        log.info("received $id")
    }
}

fun main() {
    OrderService().handle("A-1")
    val named = listOf(OrderService::class.java, Logger::class.java).joinToString(",") { it.module.name }
    val bound = ModuleLayer.boot().findModule("io.akki.slf4j").isPresent
    println("AKKI modules=$named slf4j=$bound")
}
