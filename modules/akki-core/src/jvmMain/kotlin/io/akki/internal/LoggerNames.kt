package io.akki.internal

import io.akki.LOGGER_NAME_STYLE_PROPERTY_NAME
import io.akki.LOGGER_NAME_STYLE_VALUE_JVM_CLASS
import io.akki.LOGGER_NAME_STYLE_VALUE_SOURCE
import kotlin.metadata.ClassKind
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.kind
import kotlin.reflect.KClass

private const val FACADE_SUFFIX: String = "Kt"
private const val MULTIFILE_PART_DELIMITER: String = "__"

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
    jvmLoggerNameStyleOrNull(value) ?: akkiError(invalidLoggerNameStyle(value))

private fun jvmLoggerNameStyleOrNull(value: String?): JvmLoggerNameStyle? =
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
    declaringClass?.takeIf { isCompanionObject() }?.let { return it }
    if (canonicalName != null) return null
    return enclosingClass ?: indyHost()
}

private fun Class<*>.isCompanionObject(): Boolean {
    val metadata = kotlinMetadata?.takeIf { it.kind == KotlinClassMetadata.CLASS_KIND } ?: return false
    val declaration = metadata.readLenientOrNull() as? KotlinClassMetadata.Class ?: return false
    return declaration.kmClass.kind == ClassKind.COMPANION_OBJECT
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
        KotlinClassMetadata.FILE_FACADE_KIND, KotlinClassMetadata.MULTI_FILE_CLASS_PART_KIND -> fileClassName(metadata)
        else -> ignoringMalformedClass { kotlin.qualifiedName } ?: name
    }
}

private fun Class<*>.fileClassName(metadata: Metadata): String {
    val shortName = name.substringAfterLast('.')
    val stem = if (metadata.kind == KotlinClassMetadata.MULTI_FILE_CLASS_PART_KIND) {
        shortName.removePrefix(metadata.extraString.substringAfterLast('/') + MULTIFILE_PART_DELIMITER)
    } else {
        shortName
    }.removeSuffix(FACADE_SUFFIX)
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

private fun Metadata.readLenientOrNull(): KotlinClassMetadata? =
    try {
        KotlinClassMetadata.readLenient(this)
    } catch (_: IllegalArgumentException) {
        null
    }

internal actual fun platformDeclarationName(sourceName: String, jvmClassName: String): String =
    when (configuredStyle) {
        JvmLoggerNameStyle.SOURCE -> sourceName
        JvmLoggerNameStyle.JVM_CLASS -> jvmClassName
    }
