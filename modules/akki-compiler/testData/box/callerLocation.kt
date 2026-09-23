// WITH_LOGBACK
package fixture

import helpers.capturedCallers
import io.akki.*
import kotlin.test.assertEquals

fun box(): String {
    val expected = mutableListOf<Int>()
    val callers = capturedCallers {
        expected += Throwable().stackTrace[0].lineNumber + 1
        Log.named("caller").info("located")
        expected += Throwable().stackTrace[0].lineNumber + 1
        Log.named("caller").info {
            val message = "lazy"
            message
        }
        expected += Throwable().stackTrace[0].lineNumber + 1
        Log.named("caller").info(
            fields = mapOf("id" to 1),
            message = "named",
            cause = IllegalStateException("cause"),
        )
    }
    assertEquals(expected.map { "fixture.CallerLocationKt|box|$it" }, callers.map {
        "${it.className}|${it.methodName}|${it.lineNumber}"
    })
    return "OK"
}
