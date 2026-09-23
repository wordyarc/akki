// WITH_LOGBACK
package fixture

import helpers.capturedCallers
import io.akki.*
import kotlin.test.assertEquals

fun box(): String {
    val caller = capturedCallers { Log.named("caller").info("located") }.single()
    assertEquals("fixture.CallerFrameKt|box", "${caller.className}|${caller.methodName}")
    return "OK"
}
