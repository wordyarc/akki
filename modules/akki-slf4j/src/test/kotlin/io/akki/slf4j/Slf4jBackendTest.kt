package io.akki.slf4j

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.akki.DelicateAkkiApi
import io.akki.Level
import io.akki.Log
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

@OptIn(DelicateAkkiApi::class)
class Slf4jBackendTest {
    private lateinit var appender: CallerCapturingAppender
    private lateinit var installation: Log.Installation
    private val backend = Slf4jBackend()

    @BeforeTest
    fun install() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.reset()
        appender = CallerCapturingAppender().apply { start() }
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).apply {
            level = LogbackLevel.TRACE
            addAppender(appender)
        }
        installation = Log.install(backend)
    }

    @AfterTest
    fun uninstall() {
        installation.close()
    }

    @Test
    fun `reports the caller location of user code`() {
        Log.named("caller").info("located")

        val caller = appender.list.single().callerData.first()
        assertEquals(Slf4jBackendTest::class.java.name, caller.className)
        assertEquals("reports the caller location of user code", caller.methodName)
    }

    @Test
    fun `maps levels one to one`() {
        val logger = Log.named("levels")
        logger.trace("t")
        logger.debug("d")
        logger.info("i")
        logger.warn("w")
        logger.error("e")

        assertContentEquals(
            listOf(LogbackLevel.TRACE, LogbackLevel.DEBUG, LogbackLevel.INFO, LogbackLevel.WARN, LogbackLevel.ERROR),
            appender.list.map { it.level },
        )
    }

    @Test
    fun `passes fields as key value pairs`() {
        Log.named("fields").info("with fields", fields = mapOf("user" to 42, "tenant" to null))

        val event = appender.list.single()
        assertEquals("with fields", event.message)
        assertEquals(listOf("user" to 42, "tenant" to null), event.keyValuePairs.map { it.key to it.value })
    }

    @Test
    fun `passes the cause as a throwable`() {
        Log.named("cause").error("failed", IllegalStateException("boom"))

        val proxy = appender.list.single().throwableProxy
        assertEquals(IllegalStateException::class.java.name, proxy.className)
        assertEquals("boom", proxy.message)
    }

    @Test
    fun `omits key value pairs when there are no fields`() {
        Log.named("plain").info("no fields")

        assertNull(appender.list.single().keyValuePairs)
    }

    @Test
    fun `a binding reflects enabled levels`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger("quiet")
        logger.level = LogbackLevel.WARN
        val binding = backend.bind("quiet")

        assertNull(binding.resolve(Level.INFO))
        assertTrue(binding.resolve(Level.WARN) != null)

        logger.level = LogbackLevel.DEBUG
        assertTrue(binding.resolve(Level.INFO) != null)
    }

    @Test
    fun `a retained sink respects subsequent level changes`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger("retained")
        logger.level = LogbackLevel.DEBUG
        val sink = assertNotNull(Log.named("retained").sink(Level.DEBUG))

        logger.level = LogbackLevel.WARN
        sink.emit("disabled", null, emptyMap())
        assertTrue(appender.list.isEmpty())

        logger.level = LogbackLevel.DEBUG
        sink.emit("enabled again", null, emptyMap())
        assertEquals("enabled again", appender.list.single().message)
    }
}

private class CallerCapturingAppender : ListAppender<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        event.callerData
        super.append(event)
    }
}
