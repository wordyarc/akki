// FILE: fixture/JavaCaller.java
package fixture;

import io.akki.Logger;
import kotlin.jvm.functions.Function0;

public final class JavaCaller {
    public static void write(Logger logger, Throwable cause, Function0<String> message) {
        logger.trace("eager");
        logger.trace("cause", cause);
        logger.trace(message);
        logger.trace(cause, message);
        logger.debug("eager");
        logger.debug("cause", cause);
        logger.debug(message);
        logger.debug(cause, message);
        logger.info("eager");
        logger.info("cause", cause);
        logger.info(message);
        logger.info(cause, message);
        logger.warn("eager");
        logger.warn("cause", cause);
        logger.warn(message);
        logger.warn(cause, message);
        logger.error("eager");
        logger.error("cause", cause);
        logger.error(message);
        logger.error(cause, message);
    }
}

// FILE: main.kt
package fixture

import io.akki.Level
import io.akki.Logger
import io.akki.backend.Sink
import io.akki.test.LogRecord
import io.akki.test.RecordingLogger
import kotlin.test.assertEquals

fun box(): String {
    val logger = RecordingLogger("java-overloads")
    val cause = IllegalArgumentException("failure")
    JavaCaller.write(logger, cause) { "lazy" }

    assertEquals(
        Level.entries.flatMap { level ->
            listOf(
                LogRecord(logger.name, level, "eager"),
                LogRecord(logger.name, level, "cause", cause),
                LogRecord(logger.name, level, "lazy"),
                LogRecord(logger.name, level, "lazy", cause),
            )
        },
        logger.records,
    )

    val disabled = object : Logger() {
        override val name: String = "disabled"
        override fun sink(level: Level): Sink? = null
    }
    JavaCaller.write(disabled, cause) { error("disabled message evaluated") }
    return "OK"
}
