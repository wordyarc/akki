package io.akki

import io.akki.test.LogRecord
import io.akki.test.RecordingBackend
import io.akki.test.recordLogs
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmJavaFacadeTest {
    private val cause = RuntimeException("boom")
    private val fields: Map<String, Any?> = mapOf("attempt" to 2)

    @Test
    fun `java obtains loggers from the factory`(): Unit {
        val byClass: Logger = JavaFacade.ofClass()
        val byName: Logger = JavaFacade.named("java.facade")

        assertSame(Log.of<JavaFacade>(), byClass)
        assertEquals("io.akki.JavaFacade", byClass.name)
        assertSame(Log.named("java.facade"), byName)
    }

    @Test
    fun `java writes eager records through the generated overloads`(): Unit {
        val log = Log.named("java.eager")

        val records = recordLogs { JavaFacade.eager(log, cause, fields) }

        assertEquals(
            listOf(
                LogRecord("java.eager", Level.INFO, "eager"),
                LogRecord("java.eager", Level.WARN, "eager with cause", cause),
                LogRecord("java.eager", Level.ERROR, "eager with fields", cause, fields),
            ),
            records,
        )
    }

    @Test
    fun `java writes lazy records through the generated overloads`(): Unit {
        val log = Log.named("java.lazy")

        val records = recordLogs { JavaFacade.lazy(log, cause, fields) }

        assertEquals(
            listOf(
                LogRecord("java.lazy", Level.INFO, "lazy"),
                LogRecord("java.lazy", Level.WARN, "lazy with cause", cause),
                LogRecord("java.lazy", Level.ERROR, "lazy with fields", cause, fields),
            ),
            records,
        )
    }

    @Test
    fun `java asks whether a level is enabled`(): Unit {
        val log = Log.named("java.level")

        recordLogs(Level.WARN) {
            assertFalse(JavaFacade.isEnabled(log, Level.INFO))
            assertTrue(JavaFacade.isEnabled(log, Level.WARN))
        }
    }

    @Test
    fun `java builds contexts with the operators`(): Unit {
        val context: LogContext = JavaFacade.context(mapOf("a" to 1, "b" to 2))
        val combined: LogContext = JavaFacade.combined(context, LogContext("b" to 3, "c" to 4))
        val extended: LogContext = JavaFacade.withField(context, "c", 3)
        val reduced: LogContext = JavaFacade.without(context, "b")
        val field: Any? = JavaFacade.field(context, "b")
        val empty: LogContext = JavaFacade.empty()

        assertEquals(LogContext("a" to 1, "b" to 2), context)
        assertEquals(LogContext("a" to 1, "b" to 3, "c" to 4), combined)
        assertEquals(LogContext("a" to 1, "b" to 2, "c" to 3), extended)
        assertEquals(LogContext("a" to 1), reduced)
        assertEquals(2, field)
        assertSame(LogContext.EMPTY, empty)
    }

    @Test
    fun `java enters a context with try-with-resources`(): Unit {
        val context = LogContext("request" to 7)

        val inside: LogContext = JavaFacade.inside(context)

        assertEquals(context, inside)
        assertEquals(LogContext.EMPTY, LogContext.current())
    }

    @Test
    fun `java wraps tasks for executors`(): Unit {
        val context = LogContext("request" to 7)
        val executor = Executors.newSingleThreadExecutor()
        try {
            var inRunnable: LogContext? = null
            executor.submit(JavaFacade.wrapped(context, Runnable { inRunnable = LogContext.current() })).get(10, SECONDS)
            val inCallable: LogContext = executor.submit(JavaFacade.wrapped(context, Callable { LogContext.current() }))
                .get(10, SECONDS)
            val afterwards: LogContext = executor.submit(Callable { LogContext.current() }).get(10, SECONDS)

            assertEquals(context, inRunnable)
            assertEquals(context, inCallable)
            assertEquals(LogContext.EMPTY, afterwards)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `java installs a backend for the process`(): Unit {
        val backend = RecordingBackend()

        JavaFacade.install(backend).use { Log.named("java.installed").info("installed") }

        assertEquals(listOf(LogRecord("java.installed", Level.INFO, "installed")), backend.records)
    }
}
