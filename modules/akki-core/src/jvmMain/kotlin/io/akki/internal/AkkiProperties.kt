package io.akki.internal

import io.akki.InternalAkkiApi
import java.io.IOException
import java.net.URL
import java.util.Properties

internal const val AKKI_PROPERTIES_RESOURCE: String = "META-INF/akki.properties"
internal const val LOGGER_NAME_STYLE_PROPERTY: String = "io.akki.loggerNameStyle"
internal const val BACKEND_PROPERTY: String = "io.akki.backend"

private const val PROPERTY_PREFIX: String = "io.akki."
private val CORE_PROPERTIES: List<String> = listOf(LOGGER_NAME_STYLE_PROPERTY, BACKEND_PROPERTY)

@InternalAkkiApi
public class Setting(public val value: String, public val source: String)

@InternalAkkiApi
public object AkkiProperties {
    private val resources: Map<String, Setting> by lazy {
        (AkkiProperties::class.java.classLoader ?: ClassLoader.getSystemClassLoader()).loadAkkiProperties()
    }

    public operator fun get(name: String): Setting? {
        val declared = resources[name]
        return (systemProperty(name) ?: declared)?.takeIf { it.value.isNotEmpty() }
    }

    public fun resourceSettings(prefix: String): Map<String, Setting> = resources.filterKeys { it.startsWith(prefix) }

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
        val conflicting = mutableSetOf<String>()
        for (url in resourceUrls()) {
            val properties = url.readProperties() ?: continue
            for (name in properties.stringPropertyNames()) {
                if (name !in CORE_PROPERTIES && !name.isBackendProperty()) {
                    val expected = CORE_PROPERTIES.joinToString(", ") { "'$it'" }
                    printlnToStdErr(
                        "akki: unknown property '$name' in $url: expected $expected " +
                            "or a backend setting '${PROPERTY_PREFIX}<backend>.<name>'; ignoring it",
                    )
                    continue
                }
                val declaration = Declaration(url, properties.getProperty(name).trim())
                val previous = merged.putIfAbsent(name, declaration) ?: continue
                if (previous.value != declaration.value && conflicting.add(name) && systemProperty(name) == null) {
                    printlnToStdErr(
                        "akki: conflicting values for '$name' on the classpath: " +
                            "'${previous.value}' in ${previous.url} and '${declaration.value}' in ${declaration.url}; " +
                            "ignoring the property, set the system property '$name' to choose a value",
                    )
                }
            }
        }
        return merged
            .filterKeys { it !in conflicting }
            .mapValues { (name, declaration) -> Setting(declaration.value, "'$name' in ${declaration.url}") }
    }

    private fun ClassLoader.resourceUrls(): List<URL> =
        try {
            getResources(AKKI_PROPERTIES_RESOURCE).toList()
        } catch (failure: IOException) {
            printlnToStdErr("akki: cannot list the $AKKI_PROPERTIES_RESOURCE resources: $failure; ignoring them")
            emptyList()
        }

    private fun URL.readProperties(): Properties? =
        try {
            openStream().use { Properties().apply { load(it) } }
        } catch (failure: Exception) {
            printlnToStdErr("akki: cannot read $this: $failure; ignoring its settings")
            null
        }

    private fun String.isBackendProperty(): Boolean =
        startsWith(PROPERTY_PREFIX) && '.' in removePrefix(PROPERTY_PREFIX)

    private class Declaration(val url: URL, val value: String)
}

@InternalAkkiApi
public fun <E : Enum<E>> Setting?.toEnum(default: E): E {
    if (this == null) return default
    val entries = default.declaringJavaClass.enumConstants
    val normalized = value.lowercase().replace('_', '-')
    return entries.firstOrNull { it.settingValue == normalized } ?: default.also {
        printlnToStdErr(
            "akki: unknown value '$value' set by $source: " +
                "expected ${entries.joinToString(" or ") { entry -> "'${entry.settingValue}'" }}; " +
                "using '${it.settingValue}'",
        )
    }
}

internal val Enum<*>.settingValue: String
    get() = name.lowercase().replace('_', '-')
