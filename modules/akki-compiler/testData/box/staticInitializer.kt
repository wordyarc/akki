package fixture

import io.akki.*
import kotlin.test.assertEquals

private val topLevel: String = log.name

object Holder {
    val member: String = log.name
}

fun box(): String {
    assertEquals("fixture.StaticInitializer", topLevel)
    assertEquals("fixture.Holder", Holder.member)
    return "OK"
}
