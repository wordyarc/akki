package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.LogContext
import io.akki.checkLogContextKey

@InternalAkkiApi
public fun LogContext.plusFields(fields: Array<out Pair<String, Any?>>): LogContext {
    if (fields.isEmpty()) return this
    val merged = buildMap<String, Any?>(this.fields.size + fields.size) {
        putAll(this@plusFields.fields)
        for ((key, value) in fields) {
            checkLogContextKey(key)
            put(key, value)
        }
    }
    return LogContext(merged, true)
}
