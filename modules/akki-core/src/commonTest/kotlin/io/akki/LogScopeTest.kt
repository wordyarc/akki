package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.test.RecordingBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class LogScopeTest {
    @Test
    fun `a scope receives the records of shared loggers`(): Unit {
        val backend = RecordingBackend()
        val logger = Log.named("scope.shared")

        LogScope(backend).run {
            logger.info("eager")
            logger.warn { "lazy" }
        }

        assertEquals(listOf("eager", "lazy"), backend.records.map { it.message })
        assertEquals(listOf(Level.INFO, Level.WARN), backend.records.map { it.level })
    }

    @Test
    fun `scopes nest and the outer one is restored`(): Unit {
        val outer = RecordingBackend()
        val inner = RecordingBackend()
        val logger = Log.named("scope.nested")

        LogScope(outer).run {
            logger.info("before")
            LogScope(inner).run { logger.info("inside") }
            logger.info("after")
        }

        assertEquals(listOf("before", "after"), outer.records.map { it.message })
        assertEquals(listOf("inside"), inner.records.map { it.message })
    }

    @Test
    fun `run returns the block result`(): Unit {
        assertEquals(42, LogScope(RecordingBackend()).run { 42 })
    }

    @Test
    fun `current names the entered scope`(): Unit {
        val scope = LogScope(RecordingBackend())

        assertNull(LogScope.current())
        scope.run { assertSame(scope, LogScope.current()) }
        assertNull(LogScope.current())
    }

    @Test
    fun `the scope is exited when the block throws`(): Unit {
        val scope = LogScope(RecordingBackend())

        assertFailsWith<IllegalStateException> { scope.run { error("failed") } }

        assertNull(LogScope.current())
    }

    @Test
    fun `entries are closed in reverse order`(): Unit {
        val first = LogScope(RecordingBackend()).enter()
        val second = LogScope(RecordingBackend()).enter()

        try {
            val failure = assertFailsWith<IllegalStateException> { first.close() }
            assertEquals("akki: logging scopes must be exited on their own thread in reverse order", failure.message)
        } finally {
            second.close()
            first.close()
        }

        assertNull(LogScope.current())
    }

    @Test
    fun `an entry can be closed more than once`(): Unit {
        val entry = LogScope(RecordingBackend()).enter()

        entry.close()
        entry.close()

        assertNull(LogScope.current())
    }

    @Test
    fun `a scope binds each logger once`(): Unit {
        var binds = 0
        val backend = LogBackend { _ ->
            binds++
            LoggerBinding { Sink { _, _, _ -> } }
        }
        val logger = Log.named("scope.bound-once")

        LogScope(backend).run {
            repeat(3) { logger.info("bound") }
            logger.isEnabled(Level.DEBUG)
        }

        assertEquals(1, binds)
    }

    @Test
    fun `a failing scope backend fails the caller`(): Unit {
        val logger = Log.named("scope.failing")

        LogScope(LogBackend { name -> error("broken for $name") }).run {
            val failure = assertFailsWith<IllegalStateException> { logger.info("dropped") }
            assertEquals("broken for scope.failing", failure.message)
        }
    }

    @OptIn(DelicateAkkiApi::class)
    @Test
    fun `a scope leaves the binding of the installed backend in place`(): Unit {
        var binds = 0
        val installed = LogBackend { _ ->
            binds++
            LoggerBinding { Sink { _, _, _ -> } }
        }
        val scoped = RecordingBackend()
        val logger = Log.named("scope.installed")

        Log.install(installed).use {
            logger.info("installed")
            LogScope(scoped).run { logger.info("scoped") }
            logger.info("installed again")
        }

        assertEquals(1, binds)
        assertEquals(listOf("scoped"), scoped.records.map { it.message })
    }

    @Test
    fun `a scope describes its backend`(): Unit {
        val backend = RecordingBackend()

        assertEquals("LogScope($backend)", LogScope(backend).toString())
    }
}
