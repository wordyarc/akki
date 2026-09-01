package dev.ashenarx.akki.slf4j

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.ashenarx.akki.DelicateAkkiApi
import dev.ashenarx.akki.Level
import dev.ashenarx.akki.Log
import dev.ashenarx.akki.debug
import dev.ashenarx.akki.error
import dev.ashenarx.akki.info
import dev.ashenarx.akki.trace
import dev.ashenarx.akki.warn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

@OptIn(DelicateAkkiApi::class)
class Slf4jBackendTest {
    private lateinit var appender: CallerCapturingAppender
    private lateinit var installation: Log.Installation

    @BeforeTest
    fun install() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.reset()
        appender = CallerCapturingAppender().apply { start() }
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).apply {
            level = LogbackLevel.TRACE
            addAppender(appender)
        }
        installation = Log.install(Slf4jBackend)
    }

    @AfterTest
    fun uninstall() {
        installation.uninstall()
    }

    @Test
    fun reportsCallerLocationOfUserCode() {
        Log.named("caller").info("located")

        val caller = appender.list.single().callerData.first()
        assertEquals(Slf4jBackendTest::class.java.name, caller.className)
        assertEquals("reportsCallerLocationOfUserCode", caller.methodName)
    }

    @Test
    fun mapsLevelsOneToOne() {
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
    fun passesFieldsAsKeyValuePairs() {
        Log.named("fields").info("with fields", fields = mapOf("user" to 42, "tenant" to null))

        val event = appender.list.single()
        assertEquals("with fields", event.message)
        assertEquals(listOf("user" to 42, "tenant" to null), event.keyValuePairs.map { it.key to it.value })
    }

    @Test
    fun passesCauseAsThrowable() {
        Log.named("cause").error("failed", IllegalStateException("boom"))

        val proxy = appender.list.single().throwableProxy
        assertEquals(IllegalStateException::class.java.name, proxy.className)
        assertEquals("boom", proxy.message)
    }

    @Test
    fun omitsKeyValuePairsWhenThereAreNoFields() {
        Log.named("plain").info("no fields")

        assertNull(appender.list.single().keyValuePairs)
    }

    @Test
    fun resolvesToNullWhenLevelIsDisabled() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.getLogger("quiet").level = LogbackLevel.WARN

        assertNull(Slf4jBackend.resolve("quiet", Level.INFO))
        assertTrue(Slf4jBackend.resolve("quiet", Level.WARN) != null)
    }
}

private class CallerCapturingAppender : ListAppender<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        event.callerData
        super.append(event)
    }
}
