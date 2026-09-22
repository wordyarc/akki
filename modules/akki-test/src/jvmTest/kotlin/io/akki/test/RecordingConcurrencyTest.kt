package io.akki.test

import io.akki.Log
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingConcurrencyTest {
    @Test
    fun `keeps every record written from several threads`() {
        val backend = RecordingBackend()
        val logger = Log.named("threads")

        withBackend(backend) {
            List(4) { writer -> thread { repeat(250) { logger.info("$writer-$it") } } }.forEach { it.join() }
        }

        assertEquals(1000, backend.records.size)
        assertEquals(1000, backend.records.map { it.message }.toSet().size)
    }
}
