package io.akki.system

import io.akki.BackendInstallation
import io.akki.DelicateAkkiApi
import io.akki.Level
import io.akki.Log
import io.akki.withLogFields
import java.util.logging.Handler
import java.util.logging.Level as JulLevel
import java.util.logging.LogRecord
import java.util.logging.Logger as JulLogger
import java.util.logging.SimpleFormatter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(DelicateAkkiApi::class)
class SystemLoggerBackendTest {
    private val handler = RecordingHandler()
    private val namespace: JulLogger = JulLogger.getLogger(NAMESPACE)
    private val backend = SystemLoggerBackend()
    private lateinit var installation: BackendInstallation

    @BeforeTest
    fun install() {
        namespace.level = JulLevel.ALL
        namespace.useParentHandlers = false
        namespace.addHandler(handler)
        installation = Log.install(backend)
    }

    @AfterTest
    fun uninstall() {
        installation.close()
        namespace.removeHandler(handler)
    }

    @Test
    fun `reports the caller location of user code`() {
        Log.named("$NAMESPACE.caller").info("located")

        val record = handler.records.single()
        val sourceClass: String? = record.sourceClassName
        val sourceMethod: String? = record.sourceMethodName
        assertEquals(SystemLoggerBackendTest::class.java.name, sourceClass)
        assertEquals("reports the caller location of user code", sourceMethod)
    }

    @Test
    fun `names the logger of the record`() {
        Log.named("$NAMESPACE.named").info("named")

        val loggerName: String? = handler.records.single().loggerName
        assertEquals("$NAMESPACE.named", loggerName)
    }

    @Test
    fun `maps levels one to one`() {
        val logger = Log.named("$NAMESPACE.levels")
        logger.trace("t")
        logger.debug("d")
        logger.info("i")
        logger.warn("w")
        logger.error("e")

        assertContentEquals(
            listOf(JulLevel.FINER, JulLevel.FINE, JulLevel.INFO, JulLevel.WARNING, JulLevel.SEVERE),
            handler.records.map { it.level },
        )
    }

    @Test
    fun `appends the context and then the fields to the message`() {
        withLogFields("requestId" to 7, "tenant" to "acme") {
            Log.named("$NAMESPACE.fields").info("with fields", fields = mapOf("orderId" to 42, "tenant" to "other"))
        }

        val message: String? = handler.records.single().message
        assertEquals("with fields {requestId=7, orderId=42, tenant=other}", message)
    }

    @Test
    fun `appends the context without fields`() {
        withLogFields("requestId" to 7) { Log.named("$NAMESPACE.context").info("with context") }

        val message: String? = handler.records.single().message
        assertEquals("with context {requestId=7}", message)
    }

    @Test
    fun `keeps braces and quotes of the message as written`() {
        Log.named("$NAMESPACE.plain").info("braces {0} and it's a 'quote'")

        val record = handler.records.single()
        val formatted: String? = SimpleFormatter().formatMessage(record)
        assertEquals("braces {0} and it's a 'quote'", formatted)
        assertNull(record.parameters)
    }

    @Test
    fun `passes the cause as a throwable`() {
        val cause = IllegalStateException("boom")

        Log.named("$NAMESPACE.cause").error("failed", cause)

        assertSame(cause, handler.records.single().thrown)
    }

    @Test
    fun `a binding reflects enabled levels`() {
        val quiet = JulLogger.getLogger("$NAMESPACE.quiet")
        quiet.level = JulLevel.WARNING
        val binding = backend.bind("$NAMESPACE.quiet")

        assertNull(binding.resolve(Level.INFO))
        assertNotNull(binding.resolve(Level.WARN))

        quiet.level = JulLevel.FINE
        assertNotNull(binding.resolve(Level.INFO))
    }

    @Test
    fun `a retained sink respects subsequent level changes`() {
        val retained = JulLogger.getLogger("$NAMESPACE.retained")
        retained.level = JulLevel.FINE
        val sink = assertNotNull(Log.named("$NAMESPACE.retained").sink(Level.DEBUG))

        retained.level = JulLevel.WARNING
        sink.emit("disabled", null, emptyMap())
        assertTrue(handler.records.isEmpty())

        retained.level = JulLevel.FINE
        sink.emit("enabled again", null, emptyMap())
        val message: String? = handler.records.single().message
        assertEquals("enabled again", message)
    }

    private companion object {
        const val NAMESPACE: String = "io.akki.system.backend"
    }
}

internal class RecordingHandler : Handler() {
    val records: MutableList<LogRecord> = mutableListOf()

    override fun publish(record: LogRecord) {
        record.sourceClassName
        records += record
    }

    override fun flush(): Unit = Unit

    override fun close(): Unit = Unit
}
