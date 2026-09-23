// WITH_LOGBACK
// FILE: Wrappers.kt
package fixture.wrappers

import io.akki.Level
import io.akki.Logger
import io.akki.backend.Sink

class DelegatingLogger(private val delegate: Logger) : Logger() {
    override val name: String get() = delegate.name

    override fun sink(level: Level): Sink? = delegate.sink(level)
}

class PrefixingLogger(private val delegate: Logger) : Logger() {
    override val name: String get() = delegate.name

    override fun sink(level: Level): Sink? = delegate.sink(level)?.let { inner ->
        Sink { message, cause, fields -> inner.emit("prefix $message", cause, fields) }
    }
}

// FILE: Main.kt
package fixture

import ch.qos.logback.classic.LoggerContext
import fixture.wrappers.DelegatingLogger
import fixture.wrappers.PrefixingLogger
import helpers.capturedCallers
import io.akki.Log
import kotlin.test.assertEquals
import org.slf4j.LoggerFactory

fun box(): String {
    val direct = Log.named("decorated")
    val delegating = DelegatingLogger(direct)
    val prefixing = PrefixingLogger(direct)
    val unregistered = capturedCallers { prefixing.info("unregistered") }.single()
    assertEquals(PrefixingLogger::class.java.name, unregistered.className)

    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val expected = mutableListOf<Int>()
    val wrapperPackage = "fixture.wrappers."
    val callers = try {
        capturedCallers {
            context.frameworkPackages.add(wrapperPackage)
            expected += Throwable().stackTrace[0].lineNumber + 1
            direct.info("direct")
            expected += Throwable().stackTrace[0].lineNumber + 1
            delegating.info("delegating")
            expected += Throwable().stackTrace[0].lineNumber + 1
            prefixing.info("registered")
            expected += Throwable().stackTrace[0].lineNumber + 1
            prefixing.info { "registered lazy" }
        }
    } finally {
        context.frameworkPackages.remove(wrapperPackage)
    }
    assertEquals(expected.map { "fixture.MainKt|box|$it" }, callers.map {
        "${it.className}|${it.methodName}|${it.lineNumber}"
    })
    return "OK"
}
