@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package fixture

import io.akki.Log
import io.akki.Logger
import kotlin.reflect.KClass
import kotlin.test.assertSame

private fun runtime(type: KClass<*>): Logger = Log.of(type)

private inline fun <reified T : Any> runtimeType(): KClass<T> = T::class

fun box(): String {
    val probes = listOf(
        Log.of<java.lang.Integer>() to runtime(java.lang.Integer::class),
        Log.of(java.lang.Integer::class) to runtime(java.lang.Integer::class),
        Log.of<java.lang.Void>() to runtime(java.lang.Void::class),
        Log.of<java.util.List<*>>() to runtime(java.util.List::class),
        Log.of(java.util.List::class) to runtime(java.util.List::class),
        Log.of<java.util.Map.Entry<*, *>>() to runtime(java.util.Map.Entry::class),
        Log.of(java.util.Map.Entry::class) to runtime(java.util.Map.Entry::class),
        Log.of<kotlin.jvm.functions.Function1<String, String>>() to runtime(kotlin.jvm.functions.Function1::class),
        Log.of(kotlin.jvm.functions.Function1::class) to runtime(kotlin.jvm.functions.Function1::class),
        Log.of<() -> String>() to runtime(Function0::class),
        Log.of<Int.Companion>() to runtime(Int.Companion::class),
        Log.of<String.Companion>() to runtime(String.Companion::class),
        Log.of<suspend () -> String>() to runtime(runtimeType<suspend () -> String>()),
        Log.of<suspend (String) -> String>() to runtime(runtimeType<suspend (String) -> String>()),
    )
    probes.forEach { (folded, runtime) -> assertSame(runtime, folded, "${folded.name} != ${runtime.name}") }
    return "OK"
}
