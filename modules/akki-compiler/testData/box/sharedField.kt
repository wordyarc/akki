package fixture

import io.akki.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Both {
    val viaOf: Logger = Log.of<Both>()

    fun same(): Boolean = viaOf === log
}

fun box(): String {
    assertEquals(listOf("\$\$log", "viaOf"), Both::class.java.declaredFields.map { it.name }.sorted())
    assertTrue(Both().same())
    return "OK"
}
