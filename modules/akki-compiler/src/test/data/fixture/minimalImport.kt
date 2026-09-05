package fixture

import dev.ashenarx.akki.log

class OrderService {
    fun handle(id: Int): String {
        log.info("received $id")
        return log.name
    }
}

fun box(): String = OrderService().handle(1)
