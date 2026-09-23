package fixture

import io.akki.*
import kotlin.test.assertEquals

class Host {
    fun probe(): String {
        class Local {
            fun probe(): List<String> = listOf(
                log.name,
                logger().name,
                Log.forCaller().name,
                Log.of<Local>().name,
                listOf(Unit).map { log.name }.single(),
            )
        }
        return Local().probe().joinToString(",")
    }
}

fun box(): String {
    assertEquals("fixture.Host,fixture.Host,fixture.Host,fixture.Host,fixture.Host", Host().probe())
    return "OK"
}
