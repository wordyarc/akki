package fixture

import io.akki.*
import kotlin.test.assertSame

private abstract class Base(
    val contextual: Logger,
    val anchored: Logger,
    val named: Logger,
    val typed: Logger,
) {
    val fromOverride: Logger = resolve()

    abstract fun resolve(): Logger
}

private object Site : Base(log, logger(), Log.named("audit"), Log.of<Site>()) {
    override fun resolve(): Logger = log
}

fun box(): String {
    val expected = Log.of(Site::class.java)
    assertSame(expected, Site.contextual)
    assertSame(expected, Site.anchored)
    assertSame(expected, Site.typed)
    assertSame(expected, Site.fromOverride)
    assertSame(Log.named(StringBuilder("audit").toString()), Site.named)
    return "OK"
}
