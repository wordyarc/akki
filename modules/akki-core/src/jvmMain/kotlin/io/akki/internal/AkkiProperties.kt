package io.akki.internal

import java.net.URL
import java.util.Properties

internal const val AKKI_PROPERTIES_RESOURCE: String = "META-INF/akki.properties"
internal const val LOGGER_NAME_STYLE_PROPERTY: String = "io.akki.loggerNameStyle"
internal const val BACKEND_PROPERTY: String = "io.akki.backend"

private val KNOWN_PROPERTIES: List<String> = listOf(LOGGER_NAME_STYLE_PROPERTY, BACKEND_PROPERTY)

internal class Setting(val value: String, val source: String)

internal object AkkiProperties {
    private val resources: Map<String, Setting> by lazy {
        (AkkiProperties::class.java.classLoader ?: ClassLoader.getSystemClassLoader()).loadAkkiProperties()
    }

    operator fun get(name: String): Setting? =
        (systemProperty(name) ?: resources[name])?.takeIf { it.value.isNotEmpty() }

    private fun systemProperty(name: String): Setting? {
        val value = try {
            System.getProperty(name)
        } catch (_: SecurityException) {
            null
        }
        return value?.let { Setting(it.trim(), "the system property '$name'") }
    }

    private fun ClassLoader.loadAkkiProperties(): Map<String, Setting> {
        val merged = mutableMapOf<String, Declaration>()
        for (url in getResources(AKKI_PROPERTIES_RESOURCE)) {
            val properties = url.openStream().use { Properties().apply { load(it) } }
            for (name in properties.stringPropertyNames()) {
                if (name !in KNOWN_PROPERTIES) {
                    val expected = KNOWN_PROPERTIES.joinToString(" or ") { "'$it'" }
                    akkiError("unknown property '$name' in $url: expected $expected")
                }
                val declaration = Declaration(url, properties.getProperty(name).trim())
                val previous = merged.putIfAbsent(name, declaration) ?: continue
                if (previous.value != declaration.value) {
                    akkiError(
                        "conflicting values for '$name' on the classpath: " +
                            "'${previous.value}' in ${previous.url} and '${declaration.value}' in ${declaration.url}",
                    )
                }
            }
        }
        return merged.mapValues { (name, declaration) -> Setting(declaration.value, "'$name' in ${declaration.url}") }
    }

    private class Declaration(val url: URL, val value: String)
}
