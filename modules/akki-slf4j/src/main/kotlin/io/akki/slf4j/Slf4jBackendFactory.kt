@file:OptIn(InternalAkkiApi::class)

package io.akki.slf4j

import io.akki.InternalAkkiApi
import io.akki.backend.LogBackend
import io.akki.internal.LogBackendFactory
import java.util.ServiceLoader

internal class Slf4jBackendFactory : LogBackendFactory {
    override fun createBackend(): LogBackend? = if (isProviderDeclared() && isSlf4jBound()) Slf4jBackend() else null

    override fun hintOnMissing(): String = "Add an SLF4J 2 provider to the runtime classpath, for example logback-classic."

    private fun isProviderDeclared(): Boolean {
        val provider = try {
            Class.forName(PROVIDER, false, javaClass.classLoader)
        } catch (_: ClassNotFoundException) {
            return false
        }
        return !System.getProperty(PROVIDER_PROPERTY).isNullOrEmpty() ||
            ServiceLoader.load(provider, provider.classLoader).iterator().hasNext()
    }

    private companion object {
        const val PROVIDER: String = "org.slf4j.spi.SLF4JServiceProvider"
        const val PROVIDER_PROPERTY: String = "slf4j.provider"
    }
}
