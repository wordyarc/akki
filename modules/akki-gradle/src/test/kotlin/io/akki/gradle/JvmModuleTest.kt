package io.akki.gradle

import java.lang.module.ModuleDescriptor
import java.lang.module.ModuleDescriptor.Requires.Modifier.TRANSITIVE
import java.lang.module.ModuleFinder
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmModuleTest {
    @Test
    fun `core exports its packages and uses the backend service`() {
        val core = descriptor("akki.core.jar")

        assertEquals("io.akki.core", core.name())
        assertEquals(setOf("io.akki", "io.akki.backend", "io.akki.internal"), core.exports().map { it.source() }.toSet())
        assertEquals(setOf("io.akki.backend.LogBackend"), core.uses())
        assertTrue(core.requires().any { it.name() == "kotlin.stdlib" && TRANSITIVE in it.modifiers() })
    }

    @Test
    fun `slf4j provides the backend and re-exports the core`() {
        val slf4j = descriptor("akki.slf4j.jar")

        assertEquals("io.akki.slf4j", slf4j.name())
        assertEquals(setOf("io.akki.slf4j"), slf4j.exports().map { it.source() }.toSet())
        assertEquals(
            listOf("io.akki.slf4j.Slf4jBackend"),
            slf4j.provides().single { it.service() == "io.akki.backend.LogBackend" }.providers(),
        )
        assertTrue(slf4j.requires().any { it.name() == "io.akki.core" && TRANSITIVE in it.modifiers() })
    }

    @Test
    fun `test module exports its package`() {
        val test = descriptor("akki.test.jar")

        assertEquals("io.akki.test", test.name())
        assertEquals(setOf("io.akki.test"), test.exports().map { it.source() }.toSet())
        assertTrue(test.requires().any { it.name() == "io.akki.core" && TRANSITIVE in it.modifiers() })
    }

    private fun descriptor(property: String): ModuleDescriptor {
        val descriptor = ModuleFinder.of(Path.of(System.getProperty(property))).findAll().single().descriptor()
        assertFalse(descriptor.isAutomatic, descriptor.toString())
        return descriptor
    }
}
