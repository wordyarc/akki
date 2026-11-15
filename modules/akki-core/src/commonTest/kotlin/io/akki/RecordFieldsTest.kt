@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.internal.forEachField
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordFieldsTest {
    @Test
    fun `visits the context fields and then the record fields`(): Unit {
        val context = LogContext("requestId" to 7, "tenant" to "acme")

        assertEquals(
            listOf("requestId" to 7, "tenant" to "acme", "orderId" to 42),
            context.visited(mapOf("orderId" to 42)),
        )
    }

    @Test
    fun `a record field replaces the context field with its key at the place of the record field`(): Unit {
        val context = LogContext("requestId" to 7, "tenant" to "acme")

        assertEquals(
            listOf("requestId" to 7, "orderId" to 42, "tenant" to "other"),
            context.visited(mapOf("orderId" to 42, "tenant" to "other")),
        )
    }

    @Test
    fun `a record field with a null value still replaces the context field`(): Unit {
        assertEquals(listOf("tenant" to null), LogContext("tenant" to "acme").visited(mapOf("tenant" to null)))
    }

    @Test
    fun `visits only the context fields of a record without fields`(): Unit {
        assertEquals(listOf("requestId" to 7), LogContext("requestId" to 7).visited(emptyMap()))
    }

    @Test
    fun `visits only the record fields in an empty context`(): Unit {
        assertEquals(listOf("orderId" to 42), LogContext.EMPTY.visited(mapOf("orderId" to 42)))
    }

    @Test
    fun `visits nothing for an empty context and a record without fields`(): Unit {
        assertEquals(emptyList(), LogContext.EMPTY.visited(emptyMap()))
    }

    private fun LogContext.visited(recordFields: Map<String, Any?>): List<Pair<String, Any?>> = buildList {
        forEachField(recordFields) { key, value -> add(key to value) }
    }
}
