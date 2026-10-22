@file:OptIn(io.akki.InternalAkkiApi::class)

package helpers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.akki.internal.LogScope
import io.akki.internal.withLogScope
import io.akki.slf4j.Slf4jBackend
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CallerCapturingAppender : AppenderBase<ILoggingEvent>() {
    val callers = mutableListOf<StackTraceElement>()

    override fun append(event: ILoggingEvent) {
        callers += event.callerData.first()
    }
}

fun captureCallers(): CallerCapturingAppender {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    context.reset()
    val appender = CallerCapturingAppender().also { it.context = context; it.start() }
    context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
        level = Level.TRACE
        addAppender(appender)
    }
    return appender
}

inline fun capturedCallers(crossinline block: () -> Unit): List<StackTraceElement> {
    val appender = captureCallers()
    withLogScope(LogScope(Slf4jBackend()), block)
    return appender.callers
}
