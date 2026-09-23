package fixture

import io.akki.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Both {
    val viaOf: Logger = Log.of<Both>()

    fun same(): Boolean = viaOf === log
}

fun box(): String {
    assertEquals(listOf("viaOf"), Both::class.java.declaredFields.map { it.name })
    assertEquals(listOf("\$\$log"), Class.forName("fixture.Both\$\$Log").declaredFields.map { it.name })
    assertTrue(Both().same())
    return "OK"
}
