package dev.ashenarx.akki

import dev.ashenarx.akki.internal.JvmLoggerNameStyle
import dev.ashenarx.akki.internal.parseJvmLoggerNameStyle
import dev.ashenarx.akki.internal.platformTypeName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class JvmLoggerNamesTest {
    @Test
    fun runtimeTypeFactoryUsesLogName(): Unit {
        assertSame(Log.named("audit-processor"), AuditProcessor().log)
    }

    @Test
    fun logNameIsNotInherited(): Unit {
        val expected = byStyle(PlainProcessor::class.qualifiedName, PlainProcessor::class.java.name)

        assertEquals(expected, PlainProcessor().log.name)
    }

    @Test
    fun typeFactoriesNormalizeGeneratedOwners(): Unit {
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
    fun parsesJvmLoggerNameStyles(): Unit {
        assertEquals(JvmLoggerNameStyle.SOURCE, parseJvmLoggerNameStyle(null))
        assertEquals(JvmLoggerNameStyle.SOURCE, parseJvmLoggerNameStyle("source"))
        assertEquals(JvmLoggerNameStyle.JVM_CLASS, parseJvmLoggerNameStyle("jvm-class"))
        assertFailsWith<IllegalStateException> {
            parseJvmLoggerNameStyle("binary")
        }
    }

    @Test
    fun sourceStyleUsesKotlinNamesForMappedTypes(): Unit {
        assertEquals("kotlin.String", platformTypeName(String::class.java, JvmLoggerNameStyle.SOURCE))
        assertEquals("java.lang.String", platformTypeName(String::class.java, JvmLoggerNameStyle.JVM_CLASS))
        assertEquals(byStyle("kotlin.String", "java.lang.String"), Log.of<String>().name)
        assertSame(Log.of<String>(), Log.of(String::class.java))
    }

    @Test
    fun topLevelGeneratedTypeFactoriesUseFacadeOwner(): Unit {
        val customJvmName = customJvmNameLocalLoggers()
        val multifile = multifileLocalLoggers()
        val expectedMultifile = byStyle(
            "dev.ashenarx.akki.MultifileProbeSite",
            "dev.ashenarx.akki.SharedProbeFacade__MultifileProbeSiteKt",
        )

        assertEquals("dev.ashenarx.akki.CustomProbeFacade", customJvmName.first.name)
        assertSame(customJvmName.first, customJvmName.second)
        assertEquals(expectedMultifile, multifile.first.name)
        assertSame(multifile.first, multifile.second)
    }

    @Test
    fun similarlyNamedStaticFieldDoesNotMakeNestedClassACompanion(): Unit {
        val expected = byStyle(
            SimilarFieldOwner.Nested::class.qualifiedName,
            SimilarFieldOwner.Nested::class.java.name,
        )

        assertEquals(expected, Log.of<SimilarFieldOwner.Nested>().name)
    }

    @Test
    fun configuredJvmLoggerNameStyleIsApplied(): Unit {
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
