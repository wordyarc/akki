package dev.ashenarx.lokki

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
        val logger = Caller().logger()

        assertSame(Log.of<Caller>(), logger)
    }

    @Test
    fun intrinsicUsesClassLogName(): Unit {
        val logger = NamedCaller().logger()

        assertSame(Log.named("audit"), logger)
    }

    @Test
    fun intrinsicUsesFileLogName(): Unit {
        assertSame(Log.named("file-audit"), fileLogger())
    }

    private class Caller {
        fun logger(): Log = log
    }

    @LogName("audit")
    private class NamedCaller {
        fun logger(): Log = log
    }
}
