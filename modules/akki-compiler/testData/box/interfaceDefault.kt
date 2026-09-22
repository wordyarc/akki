// CHECK_BYTECODE_TEXT
// 2 fixture/Contract\$\$Log\.\$\$log : Lio/akki/Logger;
// 0 io/akki/IntrinsicKt
package fixture

import io.akki.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

interface Contract {
    fun probe(): String = log.name
}

class Service {
    fun probe(): String = log.name
}

private class ContractImpl : Contract

fun box(): String {
    assertEquals("fixture.Contract", ContractImpl().probe())
    assertEquals("fixture.Service", Service().probe())
    assertTrue(Class.forName("fixture.Contract\$\$Log").declaredFields.single().isSynthetic)
    assertTrue(Service::class.java.declaredFields.single().isSynthetic)
    return "OK"
}
