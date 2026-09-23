package io.akki.coroutines

import io.akki.Log
import io.akki.LogScope
import io.akki.test.RecordingBackend
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class LogScopeElementTest {
    @Test
    fun `the scope follows the coroutine across dispatchers`(): Unit = runBlocking {
        val backend = RecordingBackend()
        val scope = LogScope(backend)
        val logger = Log.named("coroutines.dispatchers")

        withContext(scope.asContextElement()) {
            logger.info("caller")
            withContext(Dispatchers.Default) {
                assertSame(scope, LogScope.current())
                logger.info("default")
            }
            withContext(Dispatchers.IO) { logger.info("io") }
        }

        assertNull(LogScope.current())
        assertEquals(listOf("caller", "default", "io"), backend.records.map { it.message })
    }

    @Test
    fun `children inherit the scope`(): Unit = runBlocking {
        val backend = RecordingBackend()
        val logger = Log.named("coroutines.children")

        withContext(LogScope(backend).asContextElement()) {
            List(4) { child ->
                launch(Dispatchers.Default) { repeat(25) { logger.info("$child-$it") } }
            }.joinAll()
        }

        assertEquals(100, backend.records.size)
        assertEquals(100, backend.records.map { it.message }.toSet().size)
    }

    @Test
    fun `an inner element replaces the outer one and gives it back`(): Unit = runBlocking {
        val outer = RecordingBackend()
        val inner = RecordingBackend()
        val logger = Log.named("coroutines.nested")

        withContext(LogScope(outer).asContextElement()) {
            logger.info("before")
            withContext(LogScope(inner).asContextElement()) {
                withContext(Dispatchers.Default) { logger.info("inside") }
            }
            withContext(Dispatchers.Default) { logger.info("after") }
        }

        assertEquals(listOf("before", "after"), outer.records.map { it.message })
        assertEquals(listOf("inside"), inner.records.map { it.message })
    }

    @Test
    fun `a suspended coroutine leaves the thread without its scope`(): Unit = runBlocking {
        val scope = LogScope(RecordingBackend())

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { thread ->
            val released = CompletableDeferred<Unit>()
            val scoped = async(thread + scope.asContextElement()) {
                assertSame(scope, LogScope.current())
                released.await()
                LogScope.current()
            }
            val unscoped = async(thread) { LogScope.current() }

            assertNull(unscoped.await())
            released.complete(Unit)
            assertSame(scope, scoped.await())
        }
    }
}
