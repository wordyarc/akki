package fixture

import dev.ashenarx.akki.*

class Service {
    private val journal = logger()

    fun probe(): String = journal.name

    fun probeOther(other: Service): String = other.journal.name

    inner class Inner {
        fun probe(): String = journal.name
    }
}

object Holder {
    private val journal = Log.forCaller()

    fun probe(): String = journal.name
}

private val topLevel = logger()

private fun fields(type: Class<*>): String =
    type.declaredFields.map { it.name }.sorted().joinToString("+")

fun box(): String = listOf(
    listOf(
        Service().probe(),
        Service().probeOther(Service()),
        Service().Inner().probe(),
        Holder.probe(),
        topLevel.name,
    ).joinToString(","),
    listOf(fields(Service::class.java), fields(Holder::class.java)).joinToString(","),
).joinToString("|")
