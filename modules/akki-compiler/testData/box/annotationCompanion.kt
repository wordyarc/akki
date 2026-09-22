// CHECK_BYTECODE_TEXT
// 3 fixture/Marker\$\$Log\.\$\$log : Lio/akki/Logger;
// 0 fixture/Marker\.\$\$log
package fixture

import io.akki.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

annotation class Marker {
    companion object {
        val captured: String = log.name

        fun probe(): String = Log.of<Marker>().name
    }
}

@Marker
class Annotated

fun box(): String {
    assertEquals("fixture.Marker", Marker.captured)
    assertEquals("fixture.Marker", Marker.probe())
    assertTrue(Annotated::class.java.isAnnotationPresent(Marker::class.java))
    assertTrue(Class.forName("fixture.Marker\$\$Log").declaredFields.single().isSynthetic)
    return "OK"
}
