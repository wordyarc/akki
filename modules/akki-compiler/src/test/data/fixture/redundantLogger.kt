package fixture

import io.akki.*

class Service {
    private val journal = logger()
    private val fromIntrinsic = log
    private val fromFactory = Log.forCaller()

    internal val shared = logger()
    private var reassigned = logger()
    private val named = Log.named("audit")
    private val runtimeType = Log.of(javaClass)

    fun probe(): String = listOf(journal, fromIntrinsic, fromFactory, shared, reassigned, named, runtimeType)
        .joinToString(",") { it.name }
}

fun local(): String {
    val here = logger()
    return here.name
}

fun box(): String = Service().probe() + "|" + local()
