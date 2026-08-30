package dev.ashenarx.akki

import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertEquals(PlainProcessor::class.java.name, logger.name)
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
}
