package fixture

import io.akki.*

interface Contract {
    fun probe(): String = log.name
}

class Service {
    fun probe(): String = log.name
}

fun box(): String = listOf(
    "Contract" to Class.forName("fixture.Contract\$\$Log"),
    "Service" to Service::class.java,
).joinToString(",") { (label, type) -> label + "=" + type.declaredFields.single().isSynthetic }
