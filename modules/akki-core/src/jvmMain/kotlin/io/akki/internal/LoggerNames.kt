package io.akki.internal

import io.akki.LOGGER_NAME_STYLE_PROPERTY_NAME
import io.akki.LOGGER_NAME_STYLE_VALUE_JVM_CLASS
import io.akki.LOGGER_NAME_STYLE_VALUE_SOURCE
import kotlin.reflect.KClass

private const val FACADE_SUFFIX: String = "Kt"
private const val MULTIFILE_PART_DELIMITER: String = "__"

private const val CLASS_KIND: Int = 1
private const val FILE_FACADE_KIND: Int = 2
private const val MULTI_FILE_CLASS_PART_KIND: Int = 5

private const val UTF8_MODE_MARKER: Char = '\u0000'
private const val CLASS_FLAGS_TAG: Int = 8
private const val DEFAULT_CLASS_FLAGS: Int = 6
private const val CLASS_KIND_FLAGS_OFFSET: Int = 6
private const val CLASS_KIND_FLAGS_MASK: Int = 7
private const val COMPANION_OBJECT_CLASS_KIND: Int = 6

internal enum class JvmLoggerNameStyle {
    SOURCE,
    JVM_CLASS,
}

private val configuredStyle: JvmLoggerNameStyle by lazy { parseJvmLoggerNameStyle(loggerNameStyleProperty()) }

private val typeNames: ClassValue<String> = object : ClassValue<String>() {
    override fun computeValue(type: Class<*>): String = platformTypeName(type, configuredStyle)
}

internal actual fun platformTypeName(type: KClass<*>): String = platformTypeName(type.java)

internal fun platformTypeName(type: Class<*>): String = typeNames.get(type)

internal fun platformTypeName(type: Class<*>, style: JvmLoggerNameStyle): String {
    val owner = generateSequence(type) { it.logicalEnclosingOwner() }.last()
    return when (style) {
        JvmLoggerNameStyle.SOURCE -> owner.sourceName()
        JvmLoggerNameStyle.JVM_CLASS -> owner.name
    }
}

internal fun parseJvmLoggerNameStyle(value: String?): JvmLoggerNameStyle =
    parseJvmLoggerNameStyleOrNull(value) ?: akkiError(invalidLoggerNameStyle(value))

private fun parseJvmLoggerNameStyleOrNull(value: String?): JvmLoggerNameStyle? =
    when (value?.trim()?.lowercase()?.replace('_', '-')?.ifEmpty { null }) {
        null, LOGGER_NAME_STYLE_VALUE_SOURCE -> JvmLoggerNameStyle.SOURCE
        LOGGER_NAME_STYLE_VALUE_JVM_CLASS -> JvmLoggerNameStyle.JVM_CLASS
        else -> null
    }

private fun invalidLoggerNameStyle(value: String?): String =
    "invalid $LOGGER_NAME_STYLE_PROPERTY_NAME value '$value': " +
        "expected '$LOGGER_NAME_STYLE_VALUE_SOURCE' or '$LOGGER_NAME_STYLE_VALUE_JVM_CLASS'"

private fun loggerNameStyleProperty(): String? =
    try {
        System.getProperty(LOGGER_NAME_STYLE_PROPERTY_NAME)
    } catch (_: SecurityException) {
        null
    }

private fun Class<*>.logicalEnclosingOwner(): Class<*>? = ignoringMalformedClass { enclosingOwner() }

private fun Class<*>.enclosingOwner(): Class<*>? {
    superclass?.takeIf(Class<*>::isEnum)?.let { return it }
    if (canonicalName == null) return enclosingClass ?: indyHost()
    return declaringClass?.takeIf { isCompanion }
}

internal val Class<*>.isCompanion: Boolean
    get() {
        val metadata = kotlinMetadata?.takeIf { it.kind == CLASS_KIND } ?: return false
        val flags = metadata.data1.classFlags() ?: return false
        return flags ushr CLASS_KIND_FLAGS_OFFSET and CLASS_KIND_FLAGS_MASK == COMPANION_OBJECT_CLASS_KIND
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
        FILE_FACADE_KIND, MULTI_FILE_CLASS_PART_KIND -> fileClassName(metadata)
        else -> ignoringMalformedClass { kotlin.qualifiedName } ?: name
    }
}

private fun Class<*>.fileClassName(metadata: Metadata): String {
    val shortName = name.substringAfterLast('.')
    val partName = if (metadata.kind == MULTI_FILE_CLASS_PART_KIND) {
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

internal actual fun platformDeclarationName(sourceName: String, jvmClassName: String): String =
    when (configuredStyle) {
        JvmLoggerNameStyle.SOURCE -> sourceName
        JvmLoggerNameStyle.JVM_CLASS -> jvmClassName
    }
