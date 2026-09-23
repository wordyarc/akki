// CHECK_BYTECODE_TEXT
// 2 fixture/First\$\$Log\.\$\$log : Lio/akki/Logger;
// 2 fixture/Second\$\$Log\.\$\$log : Lio/akki/Logger;
// 0 fixture/TwoClassesKt\.\$\$log
package fixture

import io.akki.*
import kotlin.test.assertEquals

class First {
    fun probe(): String = log.name
}

class Second {
    fun probe(): String = log.name
}

fun box(): String {
    assertEquals("fixture.First", First().probe())
    assertEquals("fixture.Second", Second().probe())
    return "OK"
}
