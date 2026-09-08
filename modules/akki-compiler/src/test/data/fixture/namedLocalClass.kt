package fixture

import dev.ashenarx.akki.*

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

fun box(): String = Host().probe()
