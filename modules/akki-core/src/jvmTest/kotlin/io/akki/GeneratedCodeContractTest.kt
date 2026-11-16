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
    fun `plusFields that withLogFields inlines is a public static method of LogContextFieldsKt`(): Unit {
        val method = Class.forName("io.akki.internal.LogContextFieldsKt")
            .getMethod("plusFields", LogContext::class.java, emptyArray<Pair<String, Any?>>().javaClass)

        assertTrue(Modifier.isPublic(method.modifiers), method.toString())
        assertTrue(Modifier.isStatic(method.modifiers), method.toString())
        assertEquals(
            LogContext("a" to 1, "b" to 3),
            method.invoke(null, LogContext("a" to 1, "b" to 2), arrayOf("b" to 3)),
        )
    }

    @Test
    fun `RecordScope that the lazy level methods inline has a public constructor and a public fields getter`(): Unit {
        val constructor = RecordScope::class.java.getConstructor(Map::class.java)
        val fields = RecordScope::class.java.getMethod("getFields")
        val initial = mapOf("a" to 1)

        assertTrue(Modifier.isPublic(constructor.modifiers), constructor.toString())
        assertTrue(Modifier.isPublic(fields.modifiers), fields.toString())
        assertEquals(Map::class.java, fields.returnType)
        assertSame(initial, fields.invoke(constructor.newInstance(initial)))
    }

    @Test
    fun `field and fields are static synthetic methods of RecordScopeKt that take the scope first`(): Unit {
        val facade = Class.forName("io.akki.RecordScopeKt")
        val field = facade.getMethod("field", RecordScope::class.java, String::class.java, Any::class.java)
        val pairs = facade.getMethod("fields", RecordScope::class.java, emptyArray<Pair<String, Any?>>().javaClass)
        val map = facade.getMethod("fields", RecordScope::class.java, Map::class.java)
        val scope = RecordScope(emptyMap())

        field.invoke(null, scope, "a", 1)
        pairs.invoke(null, scope, arrayOf("b" to 2))
        map.invoke(null, scope, mapOf("c" to 3))

        for (method in listOf(field, pairs, map)) {
            assertTrue(Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers), method.toString())
            assertTrue(method.isSynthetic, method.toString())
        }
        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3), scope.fields)
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
