package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.LogContext

@InternalAkkiApi
public inline fun LogContext.forEachFieldWith(
    recordFields: Map<String, Any?>,
    action: (key: String, value: Any?) -> Unit,
) {
    for ((key, value) in fields) if (key !in recordFields) action(key, value)
    for ((key, value) in recordFields) action(key, value)
}
