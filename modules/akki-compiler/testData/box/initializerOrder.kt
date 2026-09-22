// CHECK_BYTECODE_TEXT
// 2 fixture/Colour\$\$Log\.\$\$log : Lio/akki/Logger;
// 2 fixture/Colour\$\$Log\.\$\$log\$1 : Lio/akki/Logger;
// 0 fixture/Colour\.\$\$log
package fixture

import io.akki.*
import kotlin.test.assertEquals

enum class Colour {
    RED;

    val captured: String = log.name

    val audit: String = Log.named("audit").name
}

interface Contract {
    companion object {
        val captured: String = log.name

        val audit: String = Log.of<Contract>().name
    }
}

fun box(): String {
    assertEquals(
        listOf("fixture.Colour", "audit", "fixture.Contract", "fixture.Contract"),
        listOf(Colour.RED.captured, Colour.RED.audit, Contract.captured, Contract.audit),
    )
    return "OK"
}
