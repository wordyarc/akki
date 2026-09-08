package consumer

import io.akki.log

class OrderService {
    fun handle(id: String) {
        log.info("received $id")
    }

    fun suppressed(counter: () -> Int) {
        log.debug("counted ${counter()}")
    }
}

fun main() {
    var evaluated = 0
    val service = OrderService()
    service.handle("A-1")
    service.suppressed { ++evaluated }
    println("AKKI evaluated=$evaluated")
}
