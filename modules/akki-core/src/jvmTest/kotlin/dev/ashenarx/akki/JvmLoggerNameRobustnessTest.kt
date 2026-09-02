package dev.ashenarx.akki

import dev.ashenarx.akki.internal.JvmLoggerNameStyle
import dev.ashenarx.akki.internal.platformTypeName
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertTrue

class JvmLoggerNameRobustnessTest {
    @Test
    fun derivesNamesForEveryStandardLibraryClass(): Unit {
        val types = classesOf(Unit::class.java)
        assertTrue(types.size > 500, "expected the standard library to contribute classes, got ${types.size}")

        for (type in types) {
            for (style in JvmLoggerNameStyle.entries) {
                val name = platformTypeName(type, style)
                assertTrue(name.isNotBlank(), "${type.name} resolved to a blank name in $style")
            }
        }
    }

    private fun classesOf(anchor: Class<*>): List<Class<*>> {
        val jar = File(anchor.protectionDomain.codeSource.location.toURI())
        return ZipFile(jar).use { zip ->
            zip.entries()
                .asSequence()
                .map(java.util.zip.ZipEntry::getName)
                .filter { it.endsWith(".class") && !it.startsWith("META-INF") }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .mapNotNull { runCatching { Class.forName(it, false, anchor.classLoader) }.getOrNull() }
                .toList()
        }
    }
}
