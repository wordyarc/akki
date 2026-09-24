// WITH_HELPERS
@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package fixture

import helpers.agrees
import helpers.runtime
import helpers.runtimeType
import io.akki.Log

fun box(): String {
    agrees(Log.of<java.lang.Integer>(), runtime(java.lang.Integer::class))
    agrees(Log.of(java.lang.Integer::class), runtime(java.lang.Integer::class))
    agrees(Log.of<java.lang.Void>(), runtime(java.lang.Void::class))
    agrees(Log.of<java.util.List<*>>(), runtime(java.util.List::class))
    agrees(Log.of(java.util.List::class), runtime(java.util.List::class))
    agrees(Log.of<java.util.Map.Entry<*, *>>(), runtime(java.util.Map.Entry::class))
    agrees(Log.of(java.util.Map.Entry::class), runtime(java.util.Map.Entry::class))
    agrees(Log.of<kotlin.jvm.functions.Function1<String, String>>(), runtime(kotlin.jvm.functions.Function1::class))
    agrees(Log.of(kotlin.jvm.functions.Function1::class), runtime(kotlin.jvm.functions.Function1::class))
    agrees(Log.of<() -> String>(), runtime(Function0::class))
    agrees(Log.of<Int.Companion>(), runtime(Int.Companion::class))
    agrees(Log.of<String.Companion>(), runtime(String.Companion::class))
    agrees(Log.of<suspend () -> String>(), runtime(runtimeType<suspend () -> String>()))
    agrees(Log.of<suspend (String) -> String>(), runtime(runtimeType<suspend (String) -> String>()))
    return "OK"
}
