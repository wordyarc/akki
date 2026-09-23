// WITH_LOGBACK
// FILE: Wrappers.kt
package fixture.wrappers

import io.akki.Logger

inline fun Logger.inlineReport(message: () -> String) {
    info { message() }
}

fun Logger.report(message: () -> String): Int {
    val line = Throwable().stackTrace[0].lineNumber + 1
    info(message = message)
    return line
}

// FILE: Main.kt
package fixture

import ch.qos.logback.classic.LoggerContext
import fixture.wrappers.inlineReport
import fixture.wrappers.report
import helpers.capturedCallers
import io.akki.Log
import io.akki.Logger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

private inline fun Logger.localReport(message: () -> String) {
    info { message() }
}

fun box(): String {
    val logger = Log.named("wrapped")
    var wrapperLine = 0
    val unregistered = capturedCallers { wrapperLine = logger.report { "unregistered" } }.single()
    assertEquals("fixture.wrappers.WrappersKt", unregistered.className)
    assertEquals("report", unregistered.methodName)
    assertEquals(wrapperLine, unregistered.lineNumber)

    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val wrapperPackage = "fixture.wrappers."
    var callerLine = 0
    val callers = try {
        capturedCallers {
            context.frameworkPackages.add(wrapperPackage)
            callerLine = Throwable().stackTrace[0].lineNumber + 1
            logger.report { "registered" }
            logger.inlineReport { "inlined from another file" }
            logger.localReport { "inlined from the same file" }
        }
    } finally {
        context.frameworkPackages.remove(wrapperPackage)
    }
    assertEquals(List(3) { "fixture.MainKt" }, callers.map { it.className })
    assertEquals(List(3) { "box" }, callers.map { it.methodName })
    assertEquals(callerLine, callers.first().lineNumber)
    callers.drop(1).forEach { assertTrue(it.lineNumber > lastSourceLine(), it.toString()) }
    return "OK"
}

private fun lastSourceLine(): Int = Throwable().stackTrace[0].lineNumber
