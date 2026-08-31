package dev.ashenarx.akki

import dev.ashenarx.akki.internal.JvmLoggerNameStyle
import dev.ashenarx.akki.internal.parseJvmLoggerNameStyle
import dev.ashenarx.akki.internal.platformTypeName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class JvmLogTest {
    @Test
    fun javaClassFactoryUsesBinaryClassName(): Unit {
        assertSame(Log.of<JvmLogTest>(), Log.of(javaClass))
        assertEquals(JvmLogTest::class.java.name, Log.of(javaClass).name)
    }

    @Test
    fun intrinsicResolvesCallingClassWithoutCompilerPlugin(): Unit {
        val caller = Caller()
        val expected = Log.of<Caller>()

        assertSame(expected, caller.intrinsic())
        assertSame(expected, caller.functionAnchor())
        assertSame(expected, caller.factoryAnchor())
    }

    @Test
    fun intrinsicUsesClassLogName(): Unit {
        val caller = NamedCaller()
        val expected = Log.named("audit")

        assertSame(expected, caller.intrinsic())
        assertSame(expected, caller.functionAnchor())
        assertSame(expected, caller.factoryAnchor())
    }

    @Test
    fun intrinsicUsesFileLogName(): Unit {
        val expected = Log.named("file-audit")

        assertSame(expected, fileLogger())
        assertSame(expected, fileLoggerFromFunction())
        assertSame(expected, fileLoggerFromFactory())
    }

    @Test
    fun factoriesUseClassLogName(): Unit {
        val expected = Log.named("audit")

        assertSame(expected, Log.of<NamedCaller>())
        assertSame(expected, Log.of(NamedCaller::class))
        assertSame(expected, Log.of(NamedCaller::class.java))
    }

    @Test
    fun runtimeTypeFactoryUsesLogName(): Unit {
        assertSame(Log.named("audit-processor"), AuditProcessor().log)
    }

    @Test
    fun logNameIsNotInherited(): Unit {
        val logger = PlainProcessor().log
        val expected = when (configuredStyle()) {
            JvmLoggerNameStyle.SOURCE -> PlainProcessor::class.qualifiedName
            JvmLoggerNameStyle.JVM_CLASS -> PlainProcessor::class.java.name
        }

        assertEquals(expected, logger.name)
    }

    @Test
    fun typeFactoriesNormalizeGeneratedOwners(): Unit {
        class Local

        val anonymous = object {}
        val lambda = {}
        val expected = Log.of<JvmLogTest>()

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
        assertEquals("kotlin.String", platformTypeName(String::class.java, null, JvmLoggerNameStyle.SOURCE))
        assertEquals("java.lang.String", platformTypeName(String::class.java, null, JvmLoggerNameStyle.JVM_CLASS))
        val expected = when (configuredStyle()) {
            JvmLoggerNameStyle.SOURCE -> "kotlin.String"
            JvmLoggerNameStyle.JVM_CLASS -> "java.lang.String"
        }

        assertEquals(expected, Log.of<String>().name)
        assertSame(Log.of<String>(), Log.of(String::class.java))
    }

    @Test
    fun topLevelGeneratedTypeFactoriesUseSourceFileOwner(): Unit {
        val customJvmName = customJvmNameLocalLoggers()
        val multifile = multifileLocalLoggers()
        val expectedCustomJvmName: String
        val expectedMultifile: String
        when (configuredStyle()) {
            JvmLoggerNameStyle.SOURCE -> {
                expectedCustomJvmName = "dev.ashenarx.akki.CustomJvmNameProbeSite"
                expectedMultifile = "dev.ashenarx.akki.MultifileProbeSite"
            }
            JvmLoggerNameStyle.JVM_CLASS -> {
                expectedCustomJvmName = "dev.ashenarx.akki.CustomProbeFacade"
                expectedMultifile = "dev.ashenarx.akki.SharedProbeFacade__MultifileProbeSiteKt"
            }
        }

        assertEquals(expectedCustomJvmName, customJvmName.first.name)
        assertSame(customJvmName.first, customJvmName.second)
        assertEquals(expectedMultifile, multifile.first.name)
        assertSame(multifile.first, multifile.second)
    }

    @Test
    fun similarlyNamedStaticFieldDoesNotMakeNestedClassACompanion(): Unit {
        val expected = when (configuredStyle()) {
            JvmLoggerNameStyle.SOURCE -> SimilarFieldOwner.Nested::class.qualifiedName
            JvmLoggerNameStyle.JVM_CLASS -> SimilarFieldOwner.Nested::class.java.name
        }

        assertEquals(expected, Log.of<SimilarFieldOwner.Nested>().name)
    }

    @Test
    fun configuredJvmLoggerNameStyleIsApplied(): Unit {
        val expected = when (configuredStyle()) {
            JvmLoggerNameStyle.SOURCE -> StyleOwner.Nested::class.qualifiedName
            JvmLoggerNameStyle.JVM_CLASS -> StyleOwner.Nested::class.java.name
        }

        assertEquals(expected, Log.of<StyleOwner.Nested>().name)
    }

    private class Caller {
        fun intrinsic(): Logger = log

        fun functionAnchor(): Logger = logger()

        fun factoryAnchor(): Logger = Log.forCaller()
    }

    @LogName("audit")
    private class NamedCaller {
        fun intrinsic(): Logger = log

        fun functionAnchor(): Logger = logger()

        fun factoryAnchor(): Logger = Log.forCaller()
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

private fun configuredStyle(): JvmLoggerNameStyle =
    parseJvmLoggerNameStyle(System.getProperty("dev.ashenarx.akki.loggerNameStyle"))
