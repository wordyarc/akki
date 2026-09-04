package dev.ashenarx.akki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class JvmLogTest {
    @Test
    fun `javaClass factory matches KClass factory`(): Unit {
        assertSame(Log.of<JvmLogTest>(), Log.of(javaClass))
        assertEquals(JvmLogTest::class.java.name, Log.of(javaClass).name)
    }

    @Test
    fun `intrinsic resolves the calling class without the compiler plugin`(): Unit {
        val caller = Caller()
        val expected = Log.of<Caller>()

        assertSame(expected, caller.intrinsic())
        assertSame(expected, caller.functionAnchor())
        assertSame(expected, caller.factoryAnchor())
    }

    @Test
    fun `intrinsic uses the class LogName`(): Unit {
        val caller = NamedCaller()
        val expected = Log.named("audit")

        assertSame(expected, caller.intrinsic())
        assertSame(expected, caller.functionAnchor())
        assertSame(expected, caller.factoryAnchor())
    }

    @Test
    fun `intrinsic uses the file LogName`(): Unit {
        val expected = Log.named("file-audit")

        assertSame(expected, fileLogger())
        assertSame(expected, fileLoggerFromFunction())
        assertSame(expected, fileLoggerFromFactory())
    }

    @Test
    fun `factories use the class LogName`(): Unit {
        val expected = Log.named("audit")

        assertSame(expected, Log.of<NamedCaller>())
        assertSame(expected, Log.of(NamedCaller::class))
        assertSame(expected, Log.of(NamedCaller::class.java))
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
}
