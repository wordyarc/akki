package io.akki.internal

import java.net.URL
import java.util.Properties
import kotlin.reflect.KClass

private const val FACADE_SUFFIX: String = "Kt"
private const val MULTIFILE_PART_DELIMITER: String = "__"

private const val METADATA_KIND_CLASS: Int = 1
private const val METADATA_KIND_FILE_FACADE: Int = 2
private const val METADATA_KIND_MULTIFILE_CLASS_PART: Int = 5

private const val UTF8_MODE_MARKER: Char = '\u0000'
private const val CLASS_FLAGS_TAG: Int = 8
private const val DEFAULT_CLASS_FLAGS: Int = 6
private const val CLASS_KIND_FLAGS_OFFSET: Int = 6
private const val CLASS_KIND_FLAGS_MASK: Int = 7
private const val CLASS_KIND_COMPANION_OBJECT: Int = 6

internal const val LOGGER_NAME_STYLE_PROPERTY: String = "io.akki.loggerNameStyle"
internal const val AKKI_PROPERTIES_RESOURCE: String = "META-INF/akki.properties"

internal enum class LoggerNameStyle {
    SOURCE,
    JVM_CLASS,
    ;

    val propertyValue: String = name.lowercase().replace('_', '-')
}

private val configuredStyle: LoggerNameStyle by lazy {
    parseLoggerNameStyle(loggerNameStyleProperty() ?: loggerNameStyleResource())
}

private val loggerNames: ClassValue<String> = object : ClassValue<String>() {
    override fun computeValue(type: Class<*>): String = loggerName(type, configuredStyle)
}

internal actual fun loggerName(kClass: KClass<*>): String = loggerName(kClass.java)

internal fun loggerName(jClass: Class<*>): String = loggerNames.get(jClass)

internal fun loggerName(jClass: Class<*>, style: LoggerNameStyle): String {
    val owner = generateSequence(jClass) { ignoringMalformedClass { it.enclosingOwner() } }.last()
    return when (style) {
        LoggerNameStyle.SOURCE -> owner.sourceName()
        LoggerNameStyle.JVM_CLASS -> owner.name
    }
}

internal fun parseLoggerNameStyle(value: String?): LoggerNameStyle {
    val normalized = value?.trim()?.lowercase()?.replace('_', '-')?.ifEmpty { null } ?: return LoggerNameStyle.SOURCE
    return LoggerNameStyle.entries.firstOrNull { it.propertyValue == normalized } ?: akkiError(
        "unknown value '$value' for '$LOGGER_NAME_STYLE_PROPERTY': " +
            "expected ${LoggerNameStyle.entries.joinToString(" or ") { "'${it.propertyValue}'" }}",
    )
}

private fun loggerNameStyleProperty(): String? =
    try {
        System.getProperty(LOGGER_NAME_STYLE_PROPERTY)
    } catch (_: SecurityException) {
        null
    }

private fun loggerNameStyleResource(): String? {
    val loader = LoggerNameStyle::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
    val declarations = loader.getResources(AKKI_PROPERTIES_RESOURCE).asSequence()
        .mapNotNull { url -> url.loggerNameStyle()?.let { url to it } }
        .toList()
    val (url, value) = declarations.firstOrNull() ?: return null
    declarations.firstOrNull { (_, other) -> other != value }?.let { (otherUrl, other) ->
        akkiError(
            "conflicting values for '$LOGGER_NAME_STYLE_PROPERTY' on the classpath: " +
                "'$value' in $url and '$other' in $otherUrl",
        )
    }
    return value
}

private fun URL.loggerNameStyle(): String? =
    openStream().use { Properties().apply { load(it) } }.getProperty(LOGGER_NAME_STYLE_PROPERTY)

private fun Class<*>.enclosingOwner(): Class<*>? {
    superclass?.takeIf(Class<*>::isEnum)?.let { return it }
    if (canonicalName == null) return enclosingClass ?: indyHost()
    return declaringClass?.takeIf { isCompanion }
}

internal val Class<*>.isCompanion: Boolean
    get() {
        val metadata = kotlinMetadata?.takeIf { it.kind == METADATA_KIND_CLASS } ?: return false
        val flags = metadata.data1.classFlags() ?: return false
        return flags ushr CLASS_KIND_FLAGS_OFFSET and CLASS_KIND_FLAGS_MASK == CLASS_KIND_COMPANION_OBJECT
    }

private fun Array<String>.classFlags(): Int? {
    val bytes = joinToString("").iterator()
    if (!bytes.hasNext() || bytes.nextChar() != UTF8_MODE_MARKER) return null
    return try {
        repeat(bytes.nextVarint()) { bytes.nextChar() }
        if (bytes.hasNext() && bytes.nextChar().code == CLASS_FLAGS_TAG) bytes.nextVarint() else DEFAULT_CLASS_FLAGS
    } catch (_: IndexOutOfBoundsException) {
        null
    }
}

private fun CharIterator.nextVarint(): Int {
    var value = 0
    var shift = 0
    do {
        val byte = nextChar().code
        value = value or (byte and 0x7f shl shift)
        shift += 7
    } while (byte >= 0x80)
    return value
}

private fun Class<*>.indyHost(): Class<*>? {
    val binaryName = name.substringBefore('/')
    val markerIndex = binaryName.indexOf("\$\$Lambda")
    if (markerIndex < 0) return null
    return try {
        Class.forName(binaryName.substring(0, markerIndex), false, classLoader)
    } catch (_: ClassNotFoundException) {
        null
    }
}

private fun Class<*>.sourceName(): String {
    val metadata = kotlinMetadata
    return when (metadata?.kind) {
        METADATA_KIND_FILE_FACADE, METADATA_KIND_MULTIFILE_CLASS_PART -> fileClassName(metadata)
        else -> ignoringMalformedClass { kotlin.qualifiedName } ?: name
    }
}

private fun Class<*>.fileClassName(metadata: Metadata): String {
    val shortName = name.substringAfterLast('.')
    val partName = if (metadata.kind == METADATA_KIND_MULTIFILE_CLASS_PART) {
        shortName.removePrefix(metadata.extraString.substringAfterLast('/') + MULTIFILE_PART_DELIMITER)
    } else {
        shortName
    }
    val stem = partName.removeSuffix(FACADE_SUFFIX).ifEmpty { partName }
    val packageName = metadata.packageName.ifEmpty(::getPackageName)
    return if (packageName.isEmpty()) stem else "$packageName.$stem"
}

private val Class<*>.kotlinMetadata: Metadata?
    get() = getDeclaredAnnotation(Metadata::class.java)

private inline fun <T : Any> ignoringMalformedClass(read: () -> T?): T? =
    try {
        read()
    } catch (_: LinkageError) {
        null
    }

internal actual fun loggerName(sourceName: String, jvmClassName: String): String =
    when (configuredStyle) {
        LoggerNameStyle.SOURCE -> sourceName
        LoggerNameStyle.JVM_CLASS -> jvmClassName
    }
