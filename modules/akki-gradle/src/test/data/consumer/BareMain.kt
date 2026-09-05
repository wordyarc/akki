package consumer

import dev.ashenarx.akki.log

class OrderService {
    fun handle(id: String) {
        log.info("received $id")
    }
}

fun main() {
    OrderService().handle("A-1")
}
