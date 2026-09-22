package fixture

import io.akki.log
import kotlin.test.assertEquals

class OrderService {
    fun handle(id: Int): String {
        log.info("received $id")
        return log.name
    }
}

fun box(): String {
    assertEquals("fixture.OrderService", OrderService().handle(1))
    return "OK"
}
