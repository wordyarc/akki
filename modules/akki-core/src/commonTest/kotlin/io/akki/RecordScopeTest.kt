package io.akki

import io.akki.test.LogRecord
import io.akki.test.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalAkkiApi::class)
class RecordScopeTest {
    @Test
    fun `a record carries the fields written in its lambda in the order they were written`(): Unit {
        val logger = RecordingLogger("records")

        logger.info { field("jobId", "j-1"); fields("attempt" to 2, "tenant" to null); "retrying" }

        val record = logger.records.single()
        assertEquals(
            LogRecord(
                "records",
                Level.INFO,
                "retrying",
                fields = mapOf("jobId" to "j-1", "attempt" to 2, "tenant" to null),
            ),
            record,
        )
        assertEquals(listOf("jobId", "attempt", "tenant"), record.fields.keys.toList())
    }

    @Test
    fun `field and fields write the same record`(): Unit {
        val logger = RecordingLogger("records")

        logger.info { field("a", 1); field("b", 2); "one by one" }
        logger.info { fields("a" to 1, "b" to 2); "as pairs" }
        logger.info { fields(linkedMapOf("a" to 1, "b" to 2)); "as a map" }

        assertEquals(List(3) { listOf("a" to 1, "b" to 2) }, logger.records.map { it.fields.toList() })
    }

    @Test
    fun `a map written in the lambda is added to the fields argument and is not changed`(): Unit {
        val logger = RecordingLogger("records")
        val argument = mapOf("jobId" to "j-1", "attempt" to 1)
        val written = mapOf("attempt" to 2, "tenant" to null)

        logger.info(fields = argument) { fields(written); field("queue", "main"); "retrying" }

        assertEquals(
            listOf("jobId" to "j-1", "attempt" to 2, "tenant" to null, "queue" to "main"),
            logger.records.single().fields.toList(),
        )
        assertEquals(mapOf("jobId" to "j-1", "attempt" to 1), argument)
        assertEquals(mapOf("attempt" to 2, "tenant" to null), written)
    }

    @Test
    fun `a later value replaces an earlier one`(): Unit {
        val logger = RecordingLogger("records")

        logger.info { fields("attempt" to 1, "jobId" to "j-1"); field("attempt", 2); "retrying" }

        assertEquals(mapOf("attempt" to 2, "jobId" to "j-1"), logger.records.single().fields)
    }

    @Test
    fun `fields of the lambda are added to the fields argument and replace its values`(): Unit {
        val logger = RecordingLogger("records")
        val argument = mapOf("jobId" to "j-1", "attempt" to 1)

        logger.info(fields = argument) { field("attempt", 2); field("tenant", "acme"); "retrying" }

        val record = logger.records.single()
        assertEquals(listOf("jobId" to "j-1", "attempt" to 2, "tenant" to "acme"), record.fields.toList())
        assertEquals(mapOf("jobId" to "j-1", "attempt" to 1), argument)
    }

    @Test
    fun `the fields argument reaches the backend as is while the lambda writes nothing`(): Unit {
        val argument = mapOf("jobId" to "j-1")
        val scope = RecordScope(argument)

        assertSame(argument, scope.fields)
        scope.put("attempt", 2)
        assertEquals(mapOf("jobId" to "j-1", "attempt" to 2), scope.fields)
        assertEquals(mapOf("jobId" to "j-1"), argument)
    }

    @Test
    fun `a record without fields gets an empty map`(): Unit {
        val logger = RecordingLogger("records")

        logger.info { fields(); "plain" }
        logger.info { fields(emptyMap()); "plain" }
        logger.info { "plain" }

        assertEquals(List(3) { emptyMap<String, Any?>() }, logger.records.map { it.fields })
    }

    @Test
    fun `a disabled level runs neither the fields nor the message`(): Unit {
        val logger = RecordingLogger("records", minLevel = Level.INFO)
        var evaluated = 0

        logger.debug { field("value", ++evaluated); "disabled ${++evaluated}" }

        assertEquals(0, evaluated)
        assertEquals(emptyList(), logger.records)
    }

    @Test
    fun `the cause is passed next to the fields`(): Unit {
        val logger = RecordingLogger("records")
        val cause = IllegalStateException("boom")

        logger.error(cause) { field("attempt", 3); "failed" }

        val record = logger.records.single()
        assertSame(cause, record.cause)
        assertEquals(mapOf("attempt" to 3), record.fields)
    }

    @Test
    fun `a nested record keeps its own fields`(): Unit {
        val logger = RecordingLogger("records")

        logger.info {
            logger.debug { field("inner", 1); "inner" }
            field("outer", 2)
            "outer"
        }

        assertEquals(
            listOf("inner" to mapOf("inner" to 1), "outer" to mapOf("outer" to 2)),
            logger.records.map { it.message to it.fields },
        )
    }

    @Test
    fun `a function with the scope in its context adds fields to the record that calls it`(): Unit {
        val logger = RecordingLogger("records")

        logger.info { requestFields("r-1", "acme"); "accepted" }

        assertEquals(mapOf("requestId" to "r-1", "tenant" to "acme"), logger.records.single().fields)
    }

    @Test
    fun `a function value with the scope in its context is a message`(): Unit {
        val logger = RecordingLogger("records")
        val message: context(RecordScope) () -> String = { field("source", "value"); "from a value" }

        logger.info(message = message)

        assertEquals(
            listOf(LogRecord("records", Level.INFO, "from a value", fields = mapOf("source" to "value"))),
            logger.records,
        )
    }

    @Test
    fun `this and it keep their meaning inside the lambda of a record`(): Unit {
        val logger = RecordingLogger("records")

        listOf("item").forEach { logger.info { "$it of ${this::class.simpleName}" } }

        assertEquals(listOf("item of RecordScopeTest"), logger.records.map { it.message })
    }

    @Test
    fun `toString lists the fields written so far in the format of LogContext`(): Unit {
        val scope = RecordScope(mapOf("a" to 1))

        assertEquals("RecordScope()", RecordScope(emptyMap()).toString())
        assertEquals("RecordScope(a=1)", scope.toString())
        scope.put("b", null)
        assertEquals("RecordScope(a=1, b=null)", scope.toString())
        assertEquals(
            LogContext("a" to 1, "b" to null).toString().removePrefix("LogContext"),
            scope.toString().removePrefix("RecordScope"),
        )
    }

    context(_: RecordScope)
    private fun requestFields(id: String, tenant: String) {
        fields("requestId" to id, "tenant" to tenant)
    }
}
