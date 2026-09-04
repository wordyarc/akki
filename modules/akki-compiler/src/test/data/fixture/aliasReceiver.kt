package fixture

import dev.ashenarx.akki.*

private val effects = mutableListOf<String>()

class Service {
    private val journal = logger()

    private fun record(): Service {
        effects += "receiver"
        return this
    }

    fun probe(): String = record().journal.name
}

fun box(): String {
    val names = listOf(Service().probe(), Service().probe())
    return effects.joinToString(",") + "|" + names.joinToString(",")
}
