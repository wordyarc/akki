// WITH_LOGBACK
package fixture

import helpers.capturedCallers
import io.akki.*
import kotlin.test.assertEquals

fun box(): String {
    val anchor = Throwable().stackTrace[0].lineNumber
    val caller = capturedCallers { Log.named("caller").info("located") }.single()
    val located = "${caller.className}|${caller.methodName}|${caller.lineNumber}"
    assertEquals("fixture.CallerLocationKt|box|${anchor + 1}", located)
    return "OK"
}
