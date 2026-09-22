package fixture

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.akki.*
import io.akki.slf4j.Slf4jBackend
import io.akki.test.*
import kotlin.test.assertEquals
import org.slf4j.LoggerFactory

private class Capturing : AppenderBase<ILoggingEvent>() {
    val callers = mutableListOf<StackTraceElement>()

    override fun append(event: ILoggingEvent) {
        callers += event.callerData.first()
    }
}

fun box(): String {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    context.reset()
    val appender = Capturing().also { it.context = context; it.start() }
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).apply {
        level = LogbackLevel.TRACE
        addAppender(appender)
    }
    withBackend(Slf4jBackend()) {
        Log.named("caller").info("located")
    }
    val caller = appender.callers.single()
    assertEquals("fixture.CallerFrameKt|box", "${caller.className}|${caller.methodName}")
    return "OK"
}
