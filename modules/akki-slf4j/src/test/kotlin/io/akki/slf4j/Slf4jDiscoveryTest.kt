package io.akki.slf4j

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.akki.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import org.slf4j.LoggerFactory

class Slf4jDiscoveryTest {
    @Test
    fun `logs through the backend it declares as a service`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.reset()
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).apply {
            level = LogbackLevel.TRACE
            addAppender(appender)
        }

        Log.named("discovered").info("without an install")

        val event = appender.list.single()
        assertEquals("discovered", event.loggerName)
        assertEquals("without an install", event.message)
    }
}
