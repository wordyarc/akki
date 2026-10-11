package io.akki

import io.akki.internal.LOGGER_NAME_STYLE_PROPERTY
import io.akki.internal.LoggerNameStyle
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class JvmLoggerNameStylePropertyTest {
    @Test
    fun `an unknown style fails the first lookup instead of falling back`(): Unit {
        val (exit, output) = probe("binary")

        assertNotEquals(0, exit, output)
        assertContains(
            output,
            "java.lang.IllegalStateException: akki: invalid $LOGGER_NAME_STYLE_PROPERTY value 'binary': " +
                "expected '${LoggerNameStyle.SOURCE.propertyValue}' or '${LoggerNameStyle.JVM_CLASS.propertyValue}'",
        )
        assertFalse(output.contains("name="), output)
    }

    @Test
    fun `a known style names the logger`(): Unit {
        val (exit, output) = probe(LoggerNameStyle.JVM_CLASS.propertyValue)

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.name)
    }

    private fun probe(style: String): Pair<Int, String> {
        val java = ProcessHandle.current().info().command().orElseThrow()
        val process = ProcessBuilder(
            java,
            "-D$LOGGER_NAME_STYLE_PROPERTY=$style",
            "-cp",
            System.getProperty("java.class.path"),
            NameStyleProbe::class.java.name,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }
}

object NameStyleProbe {
    class Owner {
        class Nested
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("name=" + Log.of<Owner.Nested>().name)
    }
}
