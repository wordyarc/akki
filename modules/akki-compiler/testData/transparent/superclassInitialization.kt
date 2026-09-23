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
        val runtime = Log.of(javaClass)
        runtime.info { "runtime" }
        val reified = Log.of<Child>()
        reified.info { "reified" }
        val literal = Log.of(Child::class)
        literal.info { "literal" }
        Log.named("audit").info { "named" }
        assertSame(runtime, reified)
        assertSame(runtime, literal)
    }
}

fun box(): String {
    val records = recordLogs { Child() }
    assertEquals(
        listOf(
            LogRecord("fixture.Child", Level.INFO, "runtime"),
            LogRecord("fixture.Child", Level.INFO, "reified"),
            LogRecord("fixture.Child", Level.INFO, "literal"),
            LogRecord("audit", Level.INFO, "named"),
        ),
        records,
    )
    return "OK"
}
