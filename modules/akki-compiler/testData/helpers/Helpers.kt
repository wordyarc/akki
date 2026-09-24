package helpers

import io.akki.LOGGER_NAME_STYLE_PROPERTY_NAME
import io.akki.LOGGER_NAME_STYLE_VALUE_JVM_CLASS
import io.akki.Log
import io.akki.Logger
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import kotlin.reflect.KClass
import kotlin.test.assertSame

fun <T> byStyle(source: T, jvmClass: T): T =
    if (System.getProperty(LOGGER_NAME_STYLE_PROPERTY_NAME) == LOGGER_NAME_STYLE_VALUE_JVM_CLASS) jvmClass else source

fun runtime(type: KClass<*>): Logger = Log.of(type)

fun runtime(name: String): Logger = Log.named(name)

inline fun <reified T : Any> runtimeType(): KClass<T> = T::class

fun agrees(folded: Logger, runtime: Logger): Logger {
    assertSame(runtime, folded, "${folded.name} != ${runtime.name}")
    return folded
}

fun agrees(intrinsic: Logger, type: KClass<*>, vararg folded: Logger): Logger {
    assertSame(intrinsic, Log.of(type), type.toString())
    assertSame(intrinsic, Log.of(type.java), type.toString())
    folded.forEach { assertSame(intrinsic, it, type.toString()) }
    return intrinsic
}

class Effects {
    private val marks = mutableListOf<String>()

    fun <T> mark(effect: String, value: T): T {
        marks += effect
        return value
    }

    fun mark(effect: String): String = mark(effect, effect)

    fun markingResolutions(backend: LogBackend): LogBackend = LogBackend { name ->
        val binding = backend.bind(name)
        LoggerBinding { level -> mark("resolve $level", binding.resolve(level)) }
    }

    fun toList(): List<String> = marks.toList()

    override fun toString(): String = marks.joinToString(",")
}
