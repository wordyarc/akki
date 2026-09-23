package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertSame

@OptIn(DelicateAkkiApi::class)
class JvmBackendReleaseTest {
    @Test
    fun `close releases a cached backend without another lookup`(): Unit = releasedAfterClose(Failure.NONE)

    @Test
    fun `close releases a failed bind and its report marker`(): Unit = releasedAfterClose(Failure.BIND)

    @Test
    fun `close releases a failed resolve and its report marker`(): Unit = releasedAfterClose(Failure.RESOLVE)

    private fun releasedAfterClose(failure: Failure) {
        val collected = ReferenceQueue<LogBackend>()
        val reference = cachedBackend(failure, collected)
        System.gc()
        assertSame(reference, collected.remove(10_000), "the closed backend is still reachable")
    }

    private fun cachedBackend(failure: Failure, collected: ReferenceQueue<LogBackend>): WeakReference<LogBackend> {
        val backend = TemporaryBackend(failure)
        val reference = WeakReference<LogBackend>(backend, collected)
        captureStderr {
            Log.install(backend).use { Log.named("release.$failure").info("temporary") }
        }
        return reference
    }

    private enum class Failure {
        NONE,
        BIND,
        RESOLVE,
    }

    private class TemporaryBackend(private val failure: Failure) : LogBackend {
        override fun bind(name: String): LoggerBinding {
            check(failure != Failure.BIND) { "failed bind" }
            return LoggerBinding {
                check(failure != Failure.RESOLVE) { "failed resolve" }
                Sink { _, _, _ -> }
            }
        }
    }
}
