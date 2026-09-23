// WITH_LOGBACK
// FILE: fixture/JavaCaller.java
package fixture;

import io.akki.Logger;
import java.util.Map;

public final class JavaCaller {
    public static int write(Logger logger) {
        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        logger.info("from Java", null, Map.of());
        return line;
    }
}

// FILE: main.kt
package fixture

import ch.qos.logback.classic.LoggerContext
import helpers.capturedCallers
import io.akki.Log
import kotlin.test.assertEquals
import org.slf4j.LoggerFactory

fun box(): String {
    val logger = Log.named("java-caller")
    val unregistered = capturedCallers { JavaCaller.write(logger) }.single()
    assertEquals("io.akki.Logger", unregistered.className)
    assertEquals("info", unregistered.methodName)

    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    var javaLine = 0
    var kotlinLine = 0
    val callers = try {
        capturedCallers {
            context.frameworkPackages.add("io.akki.Logger")
            javaLine = JavaCaller.write(logger)
            kotlinLine = Throwable().stackTrace[0].lineNumber + 1
            logger.info("from Kotlin")
        }
    } finally {
        context.frameworkPackages.remove("io.akki.Logger")
    }
    assertEquals(listOf("fixture.JavaCaller|write|$javaLine", "fixture.MainKt|box|$kotlinLine"), callers.map {
        "${it.className}|${it.methodName}|${it.lineNumber}"
    })
    return "OK"
}
