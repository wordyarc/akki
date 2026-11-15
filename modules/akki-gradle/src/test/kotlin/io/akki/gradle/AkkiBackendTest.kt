package io.akki.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class AkkiBackendTest {
    @Test
    fun `returns the built-in backend for its class name`() {
        assertSame(AkkiBackend.SLF4J, AkkiBackend.fromClassName("io.akki.slf4j.Slf4jBackend"))
        assertSame(AkkiBackend.SYSTEM_LOGGER, AkkiBackend.fromClassName("io.akki.system.SystemLoggerBackend"))
    }

    @Test
    fun `adds a module only for a built-in backend`() {
        assertEquals("akki-slf4j", AkkiBackend.SLF4J.module)
        assertEquals("akki-system", AkkiBackend.SYSTEM_LOGGER.module)
        assertEquals(null, AkkiBackend.NONE.module)
        assertEquals(null, AkkiBackend.fromClassName("com.example.TenantBackend").module)
    }

    @Test
    fun `selects the class of every backend but none`() {
        assertEquals("io.akki.slf4j.Slf4jBackend", AkkiBackend.SLF4J.className)
        assertEquals("io.akki.system.SystemLoggerBackend", AkkiBackend.SYSTEM_LOGGER.className)
        assertEquals("com.example.TenantBackend", AkkiBackend.fromClassName("com.example.TenantBackend").className)
        assertEquals(null, AkkiBackend.NONE.className)
    }

    @Test
    fun `compares backends by class name`() {
        val backend = AkkiBackend.fromClassName("com.example.TenantBackend")

        assertEquals(AkkiBackend.fromClassName("com.example.TenantBackend"), backend)
        assertEquals(AkkiBackend.fromClassName("com.example.TenantBackend").hashCode(), backend.hashCode())
        assertNotEquals(AkkiBackend.fromClassName("com.example.OtherBackend"), backend)
        assertNotEquals(AkkiBackend.NONE, backend)
    }

    @Test
    fun `names the class in its string form`() {
        assertEquals("AkkiBackend(io.akki.system.SystemLoggerBackend)", AkkiBackend.SYSTEM_LOGGER.toString())
        assertEquals("AkkiBackend(none)", AkkiBackend.NONE.toString())
    }

    @Test
    fun `rejects a name that is not a class name`() {
        listOf("akki-slf4j", "", "com..Backend", "com.example.1Backend", "com.example.Tenant Backend").forEach { name ->
            val failure = assertFailsWith<IllegalArgumentException> { AkkiBackend.fromClassName(name) }

            assertEquals(
                "akki: '$name' is not a class name: expected the class name of a LogBackend, " +
                    "for example com.example.TenantBackend",
                failure.message,
            )
        }
    }
}
