package fixture

import io.akki.*
import io.akki.test.LogRecord
import io.akki.test.recordLogs
import kotlin.test.assertEquals
import kotlin.test.assertSame

open class Parent {
    companion object {
        val child: Child = Child().also { it.record() }
    }
}

class Child : Parent() {
    fun record() {
        log.info { "intrinsic" }
        logger().info { "anchor" }
        assertSame(Log.of(javaClass), log)
        assertSame(log, logger())
    }
}

fun box(): String {
    val records = recordLogs { Child() }
    assertEquals(
        listOf(
            LogRecord("fixture.Child", Level.INFO, "intrinsic"),
            LogRecord("fixture.Child", Level.INFO, "anchor"),
        ),
        records,
    )
    return "OK"
}
