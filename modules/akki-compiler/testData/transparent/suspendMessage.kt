package fixture

import io.akki.Level
import io.akki.Logger
import io.akki.test.RecordingLogger
import kotlin.coroutines.*
import kotlin.test.assertEquals

private var pending: Continuation<String>? = null

private suspend fun suspended(): String = suspendCoroutine { pending = it }

private suspend fun record(logger: Logger) {
    logger.info { suspended() }
    logger.info {
        try {
            return@info suspended()
        } finally {
            logger.warn("finally")
        }
    }
}

fun box(): String {
    val logger = RecordingLogger()
    var failure: Throwable? = null
    var done = false
    suspend { record(logger) }.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            failure = result.exceptionOrNull()
            done = true
        }
    })
    assertEquals(false, done)
    repeat(2) {
        val continuation = checkNotNull(pending)
        pending = null
        continuation.resume("resumed")
    }
    failure?.let { throw it }
    assertEquals(true, done)
    assertEquals(listOf("resumed", "finally", "resumed"), logger.records.map { it.message })
    assertEquals(listOf(Level.INFO, Level.WARN, Level.INFO), logger.records.map { it.level })
    return "OK"
}
