package io.akki

import kotlin.jvm.JvmSynthetic

@ExperimentalAkkiApi
@JvmSynthetic
context(scope: RecordScope)
public fun field(key: String, value: Any?) {
    scope.put(key, value)
}

@ExperimentalAkkiApi
@JvmSynthetic
context(scope: RecordScope)
public fun fields(vararg fields: Pair<String, Any?>) {
    for ((key, value) in fields) scope.put(key, value)
}

@ExperimentalAkkiApi
@JvmSynthetic
context(scope: RecordScope)
public fun fields(fields: Map<String, Any?>) {
    for ((key, value) in fields) scope.put(key, value)
}

public class RecordScope @PublishedApi internal constructor(private val initial: Map<String, Any?>) {
    private var written: MutableMap<String, Any?>? = null

    @PublishedApi
    internal val fields: Map<String, Any?>
        get() = written ?: initial

    internal fun put(key: String, value: Any?) {
        val fields = written ?: LinkedHashMap(initial).also { written = it }
        fields[key] = value
    }

    override fun toString(): String =
        fields.entries.joinToString(prefix = "RecordScope(", postfix = ")") { (key, value) -> "$key=$value" }
}
