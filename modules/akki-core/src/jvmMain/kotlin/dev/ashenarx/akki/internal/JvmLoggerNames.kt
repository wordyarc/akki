package dev.ashenarx.akki.internal

import dev.ashenarx.akki.LogName
import kotlin.metadata.ClassKind
import kotlin.metadata.ClassName
import kotlin.metadata.isLocalClassName
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.kind
import kotlin.reflect.KClass

private const val LOGGER_NAME_STYLE_PROPERTY: String = "dev.ashenarx.akki.loggerNameStyle"
private const val FACADE_SUFFIX: String = "Kt"
private const val MULTIFILE_PART_DELIMITER: String = "__"

internal enum class JvmLoggerNameStyle {
    SOURCE,
    JVM_CLASS,
}

private val configuredStyle: JvmLoggerNameStyle by lazy {
    parseJvmLoggerNameStyle(loggerNameStyleProperty())
}

private val typeNames: ClassValue<String> = object : ClassValue<String>() {
    override fun computeValue(type: Class<*>): String = platformTypeName(type, configuredStyle)
}

internal fun freezeJvmLoggerNameStyle(): JvmLoggerNameStyle = configuredStyle

internal actual fun platformTypeName(type: KClass<*>): String = platformTypeName(type.java)

internal fun platformTypeName(type: Class<*>): String = typeNames.get(type)

internal fun platformTypeName(type: Class<*>, style: JvmLoggerNameStyle): String {
    var owner = type
    while (true) {
        owner.getDeclaredAnnotation(LogName::class.java)?.let { return it.value }
        owner = logicalEnclosingOwner(owner) ?: break
    }
    return when (style) {
        JvmLoggerNameStyle.SOURCE -> sourceName(owner)
        JvmLoggerNameStyle.JVM_CLASS -> owner.name
    }
}

internal fun parseJvmLoggerNameStyle(value: String?): JvmLoggerNameStyle =
    when (value) {
        null, "source" -> JvmLoggerNameStyle.SOURCE
        "jvm-class" -> JvmLoggerNameStyle.JVM_CLASS
        else -> error(
            "Invalid $LOGGER_NAME_STYLE_PROPERTY value '$value': expected 'source' or 'jvm-class'",
        )
    }

private fun loggerNameStyleProperty(): String? =
    try {
        System.getProperty(LOGGER_NAME_STYLE_PROPERTY)
    } catch (_: SecurityException) {
        null
    }

private fun logicalEnclosingOwner(type: Class<*>): Class<*>? {
    type.superclass?.takeIf(Class<*>::isEnum)?.let { return it }
    val metadata = type.kotlinMetadata()
    if (metadata.isCompanionObject()) type.declaringClass?.let { return it }
    if (!type.isUnstableGeneratedClass(metadata)) return null
    return type.enclosingClass ?: indyHost(type)
}

private fun KotlinClassMetadata?.isCompanionObject(): Boolean =
    this is KotlinClassMetadata.Class && kmClass.kind == ClassKind.COMPANION_OBJECT

private fun Class<*>.isUnstableGeneratedClass(metadata: KotlinClassMetadata?): Boolean =
    isLocalClass ||
        isAnonymousClass ||
        isSynthetic ||
        isHidden ||
        metadata is KotlinClassMetadata.SyntheticClass

private fun indyHost(type: Class<*>): Class<*>? {
    val binaryName = type.name.substringBefore('/')
    val markerIndex = binaryName.indexOf("\$\$Lambda")
    if (markerIndex < 0) return null
    return try {
        Class.forName(binaryName.substring(0, markerIndex), false, type.classLoader)
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: LinkageError) {
        null
    }
}

private fun sourceName(type: Class<*>): String {
    val kotlinName = when (val metadata = type.kotlinMetadata()) {
        is KotlinClassMetadata.Class ->
            metadata.kmClass.name.takeUnless(ClassName::isLocalClassName)?.replace('/', '.')
        is KotlinClassMetadata.FileFacade, is KotlinClassMetadata.MultiFileClassPart -> type.fileFacadeName()
        else -> null
    }
    return kotlinName ?: type.kotlin.qualifiedName ?: type.canonicalName ?: type.name
}

private fun Class<*>.fileFacadeName(): String {
    val stem = name
        .substringAfterLast('.')
        .substringAfterLast(MULTIFILE_PART_DELIMITER)
        .removeSuffix(FACADE_SUFFIX)
    val packageName = kotlinPackageName().ifEmpty(::getPackageName)
    return if (packageName.isEmpty()) stem else "$packageName.$stem"
}

private fun Class<*>.kotlinPackageName(): String =
    getDeclaredAnnotation(Metadata::class.java)?.packageName.orEmpty()

private fun Class<*>.kotlinMetadata(): KotlinClassMetadata? {
    val metadata = getDeclaredAnnotation(Metadata::class.java) ?: return null
    return try {
        KotlinClassMetadata.readLenient(metadata)
    } catch (_: IllegalArgumentException) {
        null
    }
}
