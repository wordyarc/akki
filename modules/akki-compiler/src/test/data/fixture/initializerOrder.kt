package fixture

import io.akki.*

enum class Colour {
    RED;

    val captured: String = log.name

    val audit: String = Log.named("audit").name
}

interface Contract {
    companion object {
        val captured: String = log.name

        val audit: String = Log.of<Contract>().name
    }
}

fun box(): String = listOf(Colour.RED.captured, Colour.RED.audit, Contract.captured, Contract.audit).joinToString(",")
