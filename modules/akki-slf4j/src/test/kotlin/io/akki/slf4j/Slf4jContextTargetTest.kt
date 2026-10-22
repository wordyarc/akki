@file:OptIn(InternalAkkiApi::class)

package io.akki.slf4j

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.akki.DelicateAkkiApi
import io.akki.InternalAkkiApi
import io.akki.Log
import io.akki.testing.captureStderr
import io.akki.withLogFields
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.slf4j.LoggerFactory
import org.slf4j.MDC

@OptIn(DelicateAkkiApi::class)
class Slf4jContextTargetTest {
    private lateinit var appender: MdcCapturingAppender

    @BeforeTest
    fun configure() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.reset()
        appender = MdcCapturingAppender().apply { start() }
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).apply {
            level = LogbackLevel.TRACE
            addAppender(appender)
        }
    }

    @AfterTest
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `the mdc target writes the context and the fields to MDC instead of key value pairs`() {
        MDC.put("traceId", "t-1")
        MDC.put("tenant", "before")

        withTarget("mdc") {
            withLogFields("requestId" to 7, "tenant" to "acme") {
                Log.named("mdc").info("record", fields = mapOf("orderId" to 42, "tenant" to "other", "user" to null))
            }
        }

        val event = appender.list.single()
        assertEquals(
            mapOf("traceId" to "t-1", "requestId" to "7", "orderId" to "42", "tenant" to "other", "user" to "null"),
            event.mdcPropertyMap,
        )
        assertNull(event.keyValuePairs)
        assertEquals(mapOf("traceId" to "t-1", "tenant" to "before"), MDC.getCopyOfContextMap())
    }

    @Test
    fun `the mdc target leaves MDC untouched for a record without context and fields`() {
        withTarget("mdc") { Log.named("mdc.plain").info("record") }

        assertEquals(emptyMap<String, String>(), appender.list.single().mdcPropertyMap)
        assertNull(MDC.getCopyOfContextMap())
    }

    @Test
    fun `the default target leaves MDC alone`() {
        withTarget("key-value-pairs") { withLogFields("requestId" to 7) { Log.named("kvp").info("record") } }

        val event = appender.list.single()
        assertEquals(emptyMap<String, String>(), event.mdcPropertyMap)
        assertEquals(listOf("requestId" to 7), event.keyValuePairs.map { it.key to it.value })
    }

    @Test
    fun `the target value ignores case and separators`() {
        withProperty(CONTEXT_PROPERTY, "KEY_VALUE_PAIRS") { Slf4jBackend() }
        withProperty(CONTEXT_PROPERTY, "Mdc") { Slf4jBackend() }
    }

    @Test
    fun `an unknown target value is reported by the backend and by its factory, which keep key value pairs`() {
        val expected = "akki: unknown value 'both' set by the system property '$CONTEXT_PROPERTY': " +
            "expected 'key-value-pairs' or 'mdc'; using 'key-value-pairs'\n"

        withProperty(CONTEXT_PROPERTY, "both") {
            assertEquals(expected, captureStderr { Slf4jBackend() })
            assertEquals(expected, captureStderr { assertNotNull(Slf4jBackendFactory().createBackend()) })
            Log.install(Slf4jBackend()).use { withLogFields("requestId" to 7) { Log.named("kvp").info("record") } }
        }

        val event = appender.list.single()
        assertEquals(emptyMap<String, String>(), event.mdcPropertyMap)
        assertEquals(listOf("requestId" to 7), event.keyValuePairs.map { it.key to it.value })
    }

    private fun withTarget(target: String, block: () -> Unit) {
        withProperty(CONTEXT_PROPERTY, target) { Log.install(Slf4jBackend()).use { block() } }
    }

    private fun <T> withProperty(name: String, value: String, block: () -> T): T {
        val previous = System.setProperty(name, value)
        try {
            return block()
        } finally {
            if (previous == null) System.clearProperty(name) else System.setProperty(name, previous)
        }
    }
}

private class MdcCapturingAppender : ListAppender<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        event.prepareForDeferredProcessing()
        super.append(event)
    }
}
