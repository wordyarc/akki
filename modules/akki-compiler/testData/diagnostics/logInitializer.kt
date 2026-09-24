// RUN_PIPELINE_TILL: BACKEND
package fixture

import io.akki.*

private val fileAnchor = logger()
private val fileIntrinsic = <!LOG_AS_INITIALIZER!>log<!>

class Service {
    private val journal = logger()
    private val fromIntrinsic = <!LOG_AS_INITIALIZER!>log<!>
    private val qualifiedIntrinsic = <!LOG_AS_INITIALIZER!>io.akki.log<!>
    internal val sharedIntrinsic: Logger = <!LOG_AS_INITIALIZER!>log<!>
    private var reassignedIntrinsic = <!LOG_AS_INITIALIZER!>log<!>
    private val computed: Logger get() = log

    internal val shared = logger()
    private var reassigned = logger()
    private val named = Log.named("audit")
    private val runtimeType = Log.of(javaClass)
    private val explicitType = Log.of<Service>()
    private val literalType = Log.of(Service::class)

    fun probe(): String = listOf(
        fileAnchor, fileIntrinsic, journal, fromIntrinsic, qualifiedIntrinsic, sharedIntrinsic, reassignedIntrinsic,
        computed, shared, reassigned, named, runtimeType, explicitType, literalType,
    ).joinToString(",") { it.name }
}

class Outer {
    private val anchored = logger()

    inner class Inner {
        fun names(): Pair<String, String> = anchored.name to log.name
    }
}

fun local(): String {
    val here = logger()
    val intrinsic = <!LOG_AS_INITIALIZER!>log<!>
    return here.name + intrinsic.name
}

<!NOTHING_TO_INLINE!>inline<!> fun cachedInInlineBody(): String {
    val cached = logger()
    val intrinsic = <!LOG_AS_INITIALIZER!>log<!>
    return cached.name + intrinsic.name
}
