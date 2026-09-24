// DUMP_KT_IR
// CHECK_BYTECODE_TEXT
// 2 fixture/Service\$\$Log\.\$\$log : Lio/akki/Logger;
// 1 io/akki/internal/LogRegistry\.forDeclaration
// 0 forCaller
// 0 io/akki/IntrinsicKt
package fixture

import io.akki.*
import kotlin.test.assertEquals

class Service {
    fun probe(): String = log.name
}

fun box(): String {
    assertEquals("fixture.Service", Service().probe())
    return "OK"
}
