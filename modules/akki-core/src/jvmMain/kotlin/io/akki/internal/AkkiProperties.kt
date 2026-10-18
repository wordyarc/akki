package io.akki.internal

import java.net.URL
import java.util.Properties

internal const val AKKI_PROPERTIES_RESOURCE: String = "META-INF/akki.properties"
internal const val LOGGER_NAME_STYLE_PROPERTY: String = "io.akki.loggerNameStyle"
internal const val BACKEND_PROPERTY: String = "io.akki.backend"

internal object AkkiProperties {
    private val resources: Map<String, String> by lazy {
        (AkkiProperties::class.java.classLoader ?: ClassLoader.getSystemClassLoader()).loadAkkiProperties()
    }

    operator fun get(name: String): String? = (systemProperty(name) ?: resources[name])?.trim()?.ifEmpty { null }

    private fun systemProperty(name: String): String? =
        try {
            System.getProperty(name)
        } catch (_: SecurityException) {
            null
        }

    private fun ClassLoader.loadAkkiProperties(): Map<String, String> {
        val merged = mutableMapOf<String, Declaration>()
        for (url in getResources(AKKI_PROPERTIES_RESOURCE)) {
            val properties = url.openStream().use { Properties().apply { load(it) } }
            for (name in properties.stringPropertyNames()) {
                val declaration = Declaration(url, properties.getProperty(name))
                val previous = merged.putIfAbsent(name, declaration) ?: continue
                if (previous.value != declaration.value) {
                    akkiError(
                        "conflicting values for '$name' on the classpath: " +
                            "'${previous.value}' in ${previous.url} and '${declaration.value}' in ${declaration.url}",
                    )
                }
            }
        }
        return merged.mapValues { (_, declaration) -> declaration.value }
    }

    private class Declaration(val url: URL, val value: String)
}
