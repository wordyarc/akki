package fixture

import dev.ashenarx.akki.*

abstract class Base {
    protected val journal = logger()

    fun probeBase(): String = journal.name
}

class Service : Base() {
    internal val shared = logger()

    fun probe(): String = shared.name
}

private fun fields(vararg types: Class<*>): String = types
    .flatMap { type -> type.declaredFields.map { it.name } }
    .filter { !it.startsWith("$") }
    .sorted()
    .joinToString("+")

fun box(): String = listOf(
    fields(Base::class.java, Service::class.java),
    listOf(Service().probeBase(), Service().probe()).joinToString(","),
).joinToString("|")
