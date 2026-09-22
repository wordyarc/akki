package fixture

import io.akki.*

fun box(): String {
    val viaProperty = ::log
    val viaFunction = ::logger
    val viaFactory = Log::forCaller
    return listOf(viaProperty.get(), viaFunction(), viaFactory()).joinToString(",") { it.name }
}
