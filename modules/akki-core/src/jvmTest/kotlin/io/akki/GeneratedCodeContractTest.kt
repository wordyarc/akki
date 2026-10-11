package io.akki

import io.akki.internal.AKKI_VERSION
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GeneratedCodeContractTest {
    @Test
    fun `declarationLogger is a public static method of DeclarationLoggers`(): Unit {
        val method = declarationLogger()

        assertTrue(Modifier.isPublic(method.modifiers), method.toString())
        assertTrue(Modifier.isStatic(method.modifiers), method.toString())
        assertEquals(Logger::class.java, method.returnType)
    }

    @Test
    fun `declarationLogger returns the registry logger named in the configured style`(): Unit {
        val logger = declarationLogger().invoke(null, "fixture.Owner.Nested", "fixture.Owner\$Nested") as Logger

        assertEquals(byStyle("fixture.Owner.Nested", "fixture.Owner\$Nested"), logger.name)
        assertSame(Log.named(logger.name), logger)
    }

    @Test
    fun `AKKI_VERSION is a static string constant of the internal package`(): Unit {
        val field = Class.forName("io.akki.internal.AkkiVersionKt").getDeclaredField("AKKI_VERSION")

        assertTrue(Modifier.isStatic(field.modifiers), field.toString())
        assertTrue(Modifier.isFinal(field.modifiers), field.toString())
        assertEquals(String::class.java, field.type)
        assertEquals(AKKI_VERSION, field.get(null))
    }

    private fun declarationLogger(): Method =
        Class.forName("io.akki.internal.DeclarationLoggers")
            .getMethod("declarationLogger", String::class.java, String::class.java)
}
