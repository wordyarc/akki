package fixture

import io.akki.*
import kotlin.test.assertEquals

class Service {
    private val journal = logger()

    fun probe(): String = journal.name

    fun probeOther(other: Service): String = other.journal.name

    inner class Inner {
        fun probe(): String = journal.name
    }
}

object Holder {
    private val journal = logger()

    fun probe(): String = journal.name
}

private val topLevel = logger()

private fun declared(type: Class<*>): String = type.declaredFields
    .map { it.name }
    .filter { !it.startsWith("$") }
    .sorted()
    .joinToString("+")

fun box(): String {
    assertEquals(
        listOf("fixture.Service", "fixture.Service", "fixture.Service", "fixture.Holder", "fixture.ExplicitLogger"),
        listOf(
            Service().probe(),
            Service().probeOther(Service()),
            Service().Inner().probe(),
            Holder.probe(),
            topLevel.name,
        ),
    )
    assertEquals(
        listOf("journal", "INSTANCE+journal"),
        listOf(declared(Service::class.java), declared(Holder::class.java)),
    )
    return "OK"
}
