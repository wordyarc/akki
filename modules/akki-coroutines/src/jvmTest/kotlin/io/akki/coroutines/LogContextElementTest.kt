package io.akki.coroutines

import io.akki.Log
import io.akki.LogContext
import io.akki.LogScope
import io.akki.test.RecordingBackend
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class LogContextElementTest {
    @Test
    fun `the context follows the coroutine across dispatchers`(): Unit = runBlocking {
        val context = LogContext("requestId" to 7)

        withContext(context.asContextElement()) {
            assertEquals(context, LogContext.current())
            withContext(Dispatchers.Default) { assertEquals(context, LogContext.current()) }
            withContext(Dispatchers.IO) { assertEquals(context, LogContext.current()) }
        }

        assertSame(LogContext.EMPTY, LogContext.current())
    }

    @Test
    fun `withLogFields adds to the context of the coroutine`(): Unit = runBlocking {
        withContext(LogContext("requestId" to 7).asContextElement()) {
            withLogFields("step" to "validate") {
                withContext(Dispatchers.Default) {
                    assertEquals(LogContext("requestId" to 7, "step" to "validate"), LogContext.current())
                }
            }
            assertEquals(LogContext("requestId" to 7), LogContext.current())
        }
    }

    @Test
    fun `an inner element replaces the outer one and gives it back`(): Unit = runBlocking {
        val outer = LogContext("a" to 1)
        val inner = LogContext("b" to 2)

        withContext(outer.asContextElement()) {
            withContext(inner.asContextElement()) {
                withContext(Dispatchers.Default) { assertEquals(inner, LogContext.current()) }
            }
            withContext(Dispatchers.Default) { assertEquals(outer, LogContext.current()) }
        }
    }

    @Test
    fun `children inherit the context`(): Unit = runBlocking {
        val backend = RecordingBackend()
        val logger = Log.named("coroutines.context.children")

        withContext(LogScope(backend).asContextElement() + LogContext("job" to "nightly").asContextElement()) {
            List(4) { child -> launch(Dispatchers.Default) { logger.info("child-$child") } }.joinAll()
        }

        assertEquals(4, backend.records.size)
        assertTrue(backend.records.all { it.context == LogContext("job" to "nightly") })
    }

    @Test
    fun `a context entered by hand does not outlive a suspension`(): Unit = runBlocking {
        val context = LogContext("a" to 1)

        withContext(context.asContextElement()) {
            val entry = LogContext("b" to 2).enter()
            assertEquals(LogContext("b" to 2), LogContext.current())
            yield()
            assertEquals(context, LogContext.current())
            assertFailsWith<IllegalStateException> { entry.close() }
        }

        assertSame(LogContext.EMPTY, LogContext.current())
    }

    @Test
    fun `a suspended coroutine leaves the thread without its context`(): Unit = runBlocking {
        val context = LogContext("a" to 1)

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { thread ->
            val released = CompletableDeferred<Unit>()
            val carried = async(thread + context.asContextElement()) {
                assertEquals(context, LogContext.current())
                released.await()
                LogContext.current()
            }
            val bare = async(thread) { LogContext.current() }

            assertSame(LogContext.EMPTY, bare.await())
            released.complete(Unit)
            assertEquals(context, carried.await())
        }
    }

    @Test
    fun `the element names its context`(): Unit {
        assertEquals("LogContextElement(LogContext(a=1))", LogContext("a" to 1).asContextElement().toString())
    }
}
