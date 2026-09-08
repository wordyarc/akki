package io.akki

import io.akki.internal.JvmLoggerNameStyle
import io.akki.internal.parseJvmLoggerNameStyle
import io.akki.internal.platformTypeName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class JvmLoggerNamesTest {
    @Test
    fun `runtime type factory uses LogName`(): Unit {
        assertSame(Log.named("audit-processor"), AuditProcessor().log)
    }

    @Test
    fun `LogName is not inherited`(): Unit {
        val expected = byStyle(PlainProcessor::class.qualifiedName, PlainProcessor::class.java.name)

        assertEquals(expected, PlainProcessor().log.name)
    }

    @Test
    fun `type factories normalize generated owners`(): Unit {
        class Local

        val anonymous = object {}
        val lambda = {}
        val expected = Log.of<JvmLoggerNamesTest>()

        assertSame(expected, Log.of(Local::class))
        assertSame(expected, Log.of(anonymous.javaClass))
        assertSame(expected, Log.of(lambda.javaClass))
        assertSame(Log.of<FactoryOwner>(), Log.of<FactoryOwner.Factory>())
    }

    @Test
    fun `parses JVM logger name styles`(): Unit {
        assertEquals(JvmLoggerNameStyle.SOURCE, parseJvmLoggerNameStyle(null))
        assertEquals(JvmLoggerNameStyle.SOURCE, parseJvmLoggerNameStyle("source"))
        assertEquals(JvmLoggerNameStyle.JVM_CLASS, parseJvmLoggerNameStyle("jvm-class"))
        assertFailsWith<IllegalStateException> {
            parseJvmLoggerNameStyle("binary")
        }
    }

    @Test
    fun `SOURCE style uses Kotlin names for mapped types`(): Unit {
        assertEquals("kotlin.String", platformTypeName(String::class.java, JvmLoggerNameStyle.SOURCE))
        assertEquals("java.lang.String", platformTypeName(String::class.java, JvmLoggerNameStyle.JVM_CLASS))
        assertEquals(byStyle("kotlin.String", "java.lang.String"), Log.of<String>().name)
        assertSame(Log.of<String>(), Log.of(String::class.java))
    }

    @Test
    fun `top-level generated type factories use the facade owner`(): Unit {
        val customJvmName = customJvmNameLocalLoggers()
        val multifile = multifileLocalLoggers()
        val expectedMultifile = byStyle(
            "io.akki.MultifileProbeSite",
            "io.akki.SharedProbeFacade__MultifileProbeSiteKt",
        )

        assertEquals("io.akki.CustomProbeFacade", customJvmName.first.name)
        assertSame(customJvmName.first, customJvmName.second)
        assertEquals(expectedMultifile, multifile.first.name)
        assertSame(multifile.first, multifile.second)
    }

    @Test
    fun `similarly named static field does not make a nested class a companion`(): Unit {
        val expected = byStyle(
            SimilarFieldOwner.Nested::class.qualifiedName,
            SimilarFieldOwner.Nested::class.java.name,
        )

        assertEquals(expected, Log.of<SimilarFieldOwner.Nested>().name)
    }

    @Test
    fun `configured JVM logger name style is applied`(): Unit {
        val expected = byStyle(StyleOwner.Nested::class.qualifiedName, StyleOwner.Nested::class.java.name)

        assertEquals(expected, Log.of<StyleOwner.Nested>().name)
    }

    private abstract class Processor {
        val log: Logger = Log.of(javaClass)
    }

    @LogName("audit-processor")
    private class AuditProcessor : Processor()

    private class PlainProcessor : Processor()

    private class FactoryOwner {
        companion object Factory
    }

    private class StyleOwner {
        class Nested
    }

    private class SimilarFieldOwner {
        class Nested private constructor() {
            companion object {
                fun create(): Nested = Nested()
            }
        }

        companion object {
            @JvmField
            val Nested: SimilarFieldOwner.Nested = SimilarFieldOwner.Nested.create()
        }
    }
}
