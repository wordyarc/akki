// RUN_PIPELINE_TILL: BACKEND
package fixture

import io.akki.*

class Service {
    private val <!REDUNDANT_LOGGER_PROPERTY!>journal<!> = logger()
    private val <!REDUNDANT_LOGGER_PROPERTY!>fromIntrinsic<!> = log

    internal val shared = logger()
    private var reassigned = logger()
    private val named = Log.named("audit")
    private val runtimeType = Log.of(javaClass)
    private val explicitType = Log.of<Service>()
    private val literalType = Log.of(Service::class)

    fun probe(): String = listOf(journal, fromIntrinsic, shared, reassigned, named, runtimeType, explicitType, literalType)
        .joinToString(",") { it.name }
}

class Outer {
    private val <!REDUNDANT_LOGGER_PROPERTY!>anchored<!> = logger()

    inner class Inner {
        fun names(): Pair<String, String> = anchored.name to log.name
    }
}

fun local(): String {
    val <!REDUNDANT_LOGGER_PROPERTY!>here<!> = logger()
    return here.name
}

<!NOTHING_TO_INLINE!>inline<!> fun cachedInInlineBody(): String {
    val cached = logger()
    return cached.name + cached.name
}
