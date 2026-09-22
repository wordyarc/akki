package fixture

import io.akki.*
import kotlin.test.assertEquals

@LogName("host")
class Host {
    fun probe(): String {
        @LogName("local-audit")
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
    assertEquals("local-audit,local-audit,local-audit,local-audit,local-audit", Host().probe())
    return "OK"
}
