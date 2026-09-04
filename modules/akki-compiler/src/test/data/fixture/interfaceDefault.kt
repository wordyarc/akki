package fixture

import dev.ashenarx.akki.*

interface Contract {
    fun probe(): String = log.name
}

class Service {
    fun probe(): String = log.name
}

fun box(): String = listOf(Contract::class.java, Service::class.java).joinToString(",") {
    it.simpleName + "=" + it.declaredFields.single().isSynthetic
}
