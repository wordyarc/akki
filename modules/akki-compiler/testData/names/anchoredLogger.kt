package fixture

import io.akki.*
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class Outer {
    private val anchored = logger()

    inner class Inner {
        fun loggers(): Pair<Logger, Logger> = anchored to log
    }
}

fun box(): String {
    val (anchored, contextual) = Outer().Inner().loggers()
    assertSame(Log.of<Outer>(), anchored)
    assertSame(Log.of<Outer.Inner>(), contextual)
    assertNotSame(anchored, contextual)
    return "OK"
}
