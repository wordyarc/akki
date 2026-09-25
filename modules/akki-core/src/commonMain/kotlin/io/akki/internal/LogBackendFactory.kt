package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.backend.LogBackend

@InternalAkkiApi
public interface LogBackendFactory {
    public fun createBackend(): LogBackend?

    public fun hintOnMissing(): String
}
