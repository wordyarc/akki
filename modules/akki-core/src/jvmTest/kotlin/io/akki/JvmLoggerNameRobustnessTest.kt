package io.akki

import io.akki.internal.JvmLoggerNameStyle
import io.akki.internal.isCompanion
import io.akki.internal.loggerName
import java.io.File
import java.util.zip.ZipFile
import kotlin.metadata.ClassKind
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.kind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmLoggerNameRobustnessTest {
    @Test
    fun `derives names for every standard library class`(): Unit {
        val types = classesOf(Unit::class.java)
        assertTrue(types.size > 500, "expected the standard library to contribute classes, got ${types.size}")

        for (type in types) {
            for (style in JvmLoggerNameStyle.entries) {
                val name = loggerName(type, style)
                assertTrue(name.isNotBlank(), "${type.name} resolved to a blank name in $style")
            }
        }
    }

    @Test
    fun `recognizes companion objects as kotlin-metadata-jvm does`(): Unit {
        val types = listOf(Unit::class.java, KotlinClassMetadata::class.java, Log::class.java, javaClass)
            .flatMap(::classesOf)
            .filter { it.isAnnotationPresent(Metadata::class.java) }
        val companions = types.filter { it.isCompanion }

        assertEquals(emptyList(), types.filter { it.isCompanion != it.isCompanionPerKotlinMetadata() })
        assertTrue(Random.Default::class.java in companions, "expected the standard library companions to be checked")
    }

    private fun classesOf(anchor: Class<*>): List<Class<*>> {
        val location = File(anchor.protectionDomain.codeSource.location.toURI())
        val entries = if (location.isDirectory) {
            location.walk().map { it.relativeTo(location).invariantSeparatorsPath }.toList()
        } else {
            ZipFile(location).use { zip -> zip.entries().asSequence().map(java.util.zip.ZipEntry::getName).toList() }
        }
        return entries
            .filter { it.endsWith(".class") && !it.startsWith("META-INF") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .mapNotNull { runCatching { Class.forName(it, false, anchor.classLoader) }.getOrNull() }
    }

    private fun Class<*>.isCompanionPerKotlinMetadata(): Boolean {
        val metadata = KotlinClassMetadata.readLenient(getDeclaredAnnotation(Metadata::class.java))
        return metadata is KotlinClassMetadata.Class && metadata.kmClass.kind == ClassKind.COMPANION_OBJECT
    }
}
