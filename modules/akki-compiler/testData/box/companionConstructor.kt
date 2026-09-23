package fixture

import io.akki.*
import kotlin.test.assertSame

private abstract class Base(val contextual: Logger, val typed: Logger) {
    val fromOverride: Logger = resolve()

    abstract fun resolve(): Logger
}

private class Site {
    companion object : Base(log, Log.of<Site>()) {
        override fun resolve(): Logger = log
    }
}

fun box(): String {
    val expected = Log.of(Site::class.java)
    assertSame(expected, Site.contextual)
    assertSame(expected, Site.typed)
    assertSame(expected, Site.fromOverride)
    return "OK"
}
