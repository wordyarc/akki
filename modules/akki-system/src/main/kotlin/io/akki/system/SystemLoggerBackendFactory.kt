@file:OptIn(InternalAkkiApi::class)

package io.akki.system

import io.akki.InternalAkkiApi
import io.akki.backend.LogBackend
import io.akki.internal.LogBackendFactory

internal class SystemLoggerBackendFactory : LogBackendFactory {
    override val backendClassName: String = SystemLoggerBackend::class.java.name

    override fun createBackend(): LogBackend = SystemLoggerBackend()

    override fun hintOnMissing(): String = "The System.Logger backend needs no setup."
}
