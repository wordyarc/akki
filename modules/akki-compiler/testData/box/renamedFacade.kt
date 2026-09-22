@file:JvmName("Orders")

package fixture

import io.akki.*
import java.lang.invoke.MethodHandles
import kotlin.test.assertEquals
import kotlin.test.assertSame

fun probe(): Logger = log

fun box(): String {
    val runtime = Log.of(MethodHandles.lookup().lookupClass())
    assertEquals("fixture.Orders", probe().name)
    assertSame(runtime, probe())
    return "OK"
}
