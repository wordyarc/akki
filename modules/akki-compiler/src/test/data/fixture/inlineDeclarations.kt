package fixture

import io.akki.*

inline fun viaIntrinsic(): String = log.name

inline fun viaAnchor(): String = logger().name

inline fun viaFactory(): String = Log.forCaller().name

inline fun withLambdaParameter(body: () -> Unit): String {
    body()
    return log.name
}

inline val viaInlineProperty: String get() = log.name

val viaInlineGetter: String inline get() = log.name

inline fun withNoinlineDefault(noinline probe: () -> String = { log.name }): String = probe()
