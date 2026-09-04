package fixture

import dev.ashenarx.akki.*

class Service {
    private var reassigned = logger()
    private val referenced = logger()

    fun probeReferenced(): String = ::referenced.get().name

    fun probeReassigned(): String {
        reassigned = Log.named(reassigned.name)
        return reassigned.name
    }
}

private fun fields(vararg types: Class<*>): String = types
    .flatMap { type -> type.declaredFields.map { it.name } }
    .filter { !it.startsWith("$") }
    .sorted()
    .joinToString("+")

fun box(): String = listOf(
    fields(Service::class.java),
    listOf(Service().probeReferenced(), Service().probeReassigned()).joinToString(","),
).joinToString("|")
