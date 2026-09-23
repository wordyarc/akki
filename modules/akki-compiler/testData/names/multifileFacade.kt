// CHECK_BYTECODE_TEXT
// 1 LDC "fixture\.MultifileFacade"
// 1 LDC "fixture\.Orders__MultifileFacadeKt"
@file:JvmMultifileClass
@file:JvmName("Orders")

package fixture

import io.akki.*
import java.lang.invoke.MethodHandles
import kotlin.test.assertSame

fun probe(): Logger = log

fun box(): String {
    val runtime = Log.of(MethodHandles.lookup().lookupClass())
    assertSame(runtime, probe())
    return "OK"
}
