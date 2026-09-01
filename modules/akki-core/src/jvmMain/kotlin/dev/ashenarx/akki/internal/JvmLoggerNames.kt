package dev.ashenarx.akki.internal

import dev.ashenarx.akki.LogName
import java.io.DataInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

private const val LOGGER_NAME_STYLE_PROPERTY: String = "dev.ashenarx.akki.loggerNameStyle"
private const val KOTLIN_CLASS: Int = 1
private const val KOTLIN_FILE_FACADE: Int = 2
private const val KOTLIN_SYNTHETIC_CLASS: Int = 3
private const val KOTLIN_MULTIFILE_PART: Int = 5
private const val KOTLIN_COMPANION_OBJECT: Int = 6

internal enum class JvmLoggerNameStyle {
    SOURCE,
    JVM_CLASS,
}

private object JvmLoggerNameConfiguration {
    private val configuredStyle: AtomicReference<JvmLoggerNameStyle?> = AtomicReference()

    fun style(): JvmLoggerNameStyle {
        configuredStyle.get()?.let { return it }
        val configured = parseJvmLoggerNameStyle(loggerNameStyleProperty())
        configuredStyle.compareAndSet(null, configured)
        return configuredStyle.get() ?: configured
    }
}

internal fun freezeJvmLoggerNameStyle(): Unit {
    JvmLoggerNameConfiguration.style()
}

internal actual fun platformTypeName(type: KClass<*>): String = platformTypeName(type.java)

internal fun platformTypeName(type: Class<*>): String = platformTypeName(type, sourceFileName = null)

internal fun platformTypeName(type: Class<*>, sourceFileName: String?): String =
    platformTypeName(type, sourceFileName, JvmLoggerNameConfiguration.style())

internal fun platformTypeName(
    type: Class<*>,
    sourceFileName: String?,
    style: JvmLoggerNameStyle,
): String {
    var owner = type
    while (true) {
        owner.getDeclaredAnnotation(LogName::class.java)?.let { return it.value }
        owner = logicalEnclosingOwner(owner) ?: break
    }
    return when (style) {
        JvmLoggerNameStyle.SOURCE -> sourceName(owner, sourceFileName)
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
    companionOwner(type)?.let { return it }
    if (!type.isUnstableGeneratedClass()) return null
    return type.enclosingClass ?: indyHost(type)
}

private fun companionOwner(type: Class<*>): Class<*>? {
    if (!type.isKotlinCompanion()) return null
    return type.declaringClass
}

private fun Class<*>.isUnstableGeneratedClass(): Boolean =
    isLocalClass ||
        isAnonymousClass ||
        isSynthetic ||
        isHidden ||
        kotlinMetadataKind == KOTLIN_SYNTHETIC_CLASS

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

private fun sourceName(type: Class<*>, sourceFileName: String?): String {
    if (!type.isFileImplementationClass()) return type.kotlin.qualifiedName ?: type.canonicalName ?: type.name
    val fileName = sourceFileName ?: sourceFiles.get(type).ifEmpty { return type.name }
    val stem = fileName.removeSuffix(".kt").removeSuffix(".kts")
    val metadataPackageName = type.getDeclaredAnnotation(Metadata::class.java)?.packageName.orEmpty()
    val packageName = metadataPackageName.ifEmpty(type::getPackageName)
    return if (packageName.isEmpty()) stem else "$packageName.$stem"
}

private fun Class<*>.isFileImplementationClass(): Boolean =
    kotlinMetadataKind == KOTLIN_FILE_FACADE || kotlinMetadataKind == KOTLIN_MULTIFILE_PART

private val Class<*>.kotlinMetadataKind: Int?
    get() = getDeclaredAnnotation(Metadata::class.java)?.kind

private fun Class<*>.isKotlinCompanion(): Boolean {
    val metadata = getDeclaredAnnotation(Metadata::class.java) ?: return false
    if (metadata.kind != KOTLIN_CLASS) return false
    val bytes = decodeMetadataBytes(metadata.data1) ?: return false
    val stringTableSize = readVarInt(bytes, 0) ?: return false
    var index = stringTableSize.nextIndex + stringTableSize.value
    if (index > bytes.size) return false
    while (index < bytes.size) {
        val tag = readVarInt(bytes, index) ?: return false
        index = tag.nextIndex
        val field = tag.value ushr 3
        val wireType = tag.value and 7
        if (field == 1 && wireType == 0) {
            val flags = readVarInt(bytes, index) ?: return false
            return (flags.value ushr 6) and 7 == KOTLIN_COMPANION_OBJECT
        }
        index = skipProtobufValue(bytes, index, wireType) ?: return false
    }
    return false
}

private fun decodeMetadataBytes(data: Array<String>): ByteArray? {
    if (data.isEmpty()) return ByteArray(0)
    return when (data[0].firstOrNull()) {
        '\u0000' -> combineMetadataStrings(data, dropMarker = true)
        '\uFFFF' -> decode7To8(combineMetadataStrings(data, dropMarker = true))
        else -> decode7To8(combineMetadataStrings(data, dropMarker = false))
    }
}

private fun combineMetadataStrings(data: Array<String>, dropMarker: Boolean): ByteArray {
    val size = data.sumOf(String::length) - if (dropMarker) 1 else 0
    val result = ByteArray(size)
    var index = 0
    data.forEachIndexed { stringIndex, value ->
        val start = if (dropMarker && stringIndex == 0) 1 else 0
        for (characterIndex in start until value.length) {
            result[index++] = value[characterIndex].code.toByte()
        }
    }
    return result
}

private fun decode7To8(encoded: ByteArray): ByteArray? {
    for (index in encoded.indices) {
        encoded[index] = ((encoded[index].toInt() + 0x7f) and 0x7f).toByte()
    }
    val result = ByteArray(7 * encoded.size / 8)
    var byteIndex = 0
    var bit = 0
    for (index in result.indices) {
        if (byteIndex + 1 >= encoded.size) return null
        val first = (encoded[byteIndex].toInt() and 0xff) ushr bit
        byteIndex++
        val second = (encoded[byteIndex].toInt() and ((1 shl (bit + 1)) - 1)) shl (7 - bit)
        result[index] = (first + second).toByte()
        if (bit == 6) {
            byteIndex++
            bit = 0
        } else {
            bit++
        }
    }
    return result
}

private fun readVarInt(bytes: ByteArray, startIndex: Int): VarInt? {
    var index = startIndex
    var value = 0
    var shift = 0
    while (index < bytes.size && shift < 32) {
        val byte = bytes[index].toInt() and 0xff
        value = value or ((byte and 0x7f) shl shift)
        index++
        if (byte and 0x80 == 0) return VarInt(value, index)
        shift += 7
    }
    return null
}

private fun skipProtobufValue(bytes: ByteArray, index: Int, wireType: Int): Int? =
    when (wireType) {
        0 -> readVarInt(bytes, index)?.nextIndex
        1 -> (index + 8).takeIf { it <= bytes.size }
        2 -> readVarInt(bytes, index)?.let { size ->
            (size.nextIndex + size.value).takeIf { it <= bytes.size }
        }
        5 -> (index + 4).takeIf { it <= bytes.size }
        else -> null
    }

private data class VarInt(val value: Int, val nextIndex: Int)

private val sourceFiles: ClassValue<String> = object : ClassValue<String>() {
    override fun computeValue(type: Class<*>): String = readSourceFileName(type).orEmpty()
}

private fun readSourceFileName(type: Class<*>): String? {
    val binaryName = type.name.substringBefore('/')
    val resourceName = "/${binaryName.replace('.', '/')}.class"
    val stream = type.getResourceAsStream(resourceName) ?: return null
    return try {
        stream.use { parseSourceFileName(DataInputStream(it)) }
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }
}

private fun parseSourceFileName(input: DataInputStream): String? {
    if (input.readInt() != 0xCAFEBABE.toInt()) return null
    input.skipNBytes(4)
    val constants = arrayOfNulls<String>(input.readUnsignedShort())
    var index = 1
    while (index < constants.size) {
        when (input.readUnsignedByte()) {
            1 -> constants[index] = input.readUTF()
            3, 4 -> input.skipNBytes(4)
            5, 6 -> {
                input.skipNBytes(8)
                index++
            }
            7, 8, 16, 19, 20 -> input.skipNBytes(2)
            9, 10, 11, 12, 17, 18 -> input.skipNBytes(4)
            15 -> input.skipNBytes(3)
            else -> return null
        }
        index++
    }
    input.skipNBytes(6)
    repeat(input.readUnsignedShort()) { input.skipNBytes(2) }
    skipMembers(input)
    skipMembers(input)
    repeat(input.readUnsignedShort()) {
        val name = constants[input.readUnsignedShort()]
        val size = input.readInt().toLong() and 0xffffffffL
        if (name == "SourceFile" && size == 2L) {
            return constants[input.readUnsignedShort()]
        }
        input.skipNBytes(size)
    }
    return null
}

private fun skipMembers(input: DataInputStream): Unit = repeat(input.readUnsignedShort()) {
    input.skipNBytes(6)
    repeat(input.readUnsignedShort()) {
        input.skipNBytes(2)
        input.skipNBytes(input.readInt().toLong() and 0xffffffffL)
    }
}
