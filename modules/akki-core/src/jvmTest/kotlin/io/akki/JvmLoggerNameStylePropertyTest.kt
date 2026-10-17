package io.akki

import io.akki.internal.AKKI_PROPERTIES_RESOURCE
import io.akki.internal.LOGGER_NAME_STYLE_PROPERTY
import io.akki.internal.LoggerNameStyle
import java.io.File
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.io.TempDir

class JvmLoggerNameStylePropertyTest {
    @Test
    fun `an unknown style fails the first lookup instead of falling back`(): Unit {
        val (exit, output) = probe(property = "binary")

        assertNotEquals(0, exit, output)
        assertContains(output, unknownValue("binary"))
        assertFalse(output.contains("name="), output)
    }

    @Test
    fun `a known style names the logger`(): Unit {
        val (exit, output) = probe(property = LoggerNameStyle.JVM_CLASS.propertyValue)

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.name)
    }

    @Test
    fun `a classpath resource selects the style`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(resources = listOf(resource(directory, LoggerNameStyle.JVM_CLASS.propertyValue)))

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.name)
    }

    @Test
    fun `the system property overrides a classpath resource`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(
            property = LoggerNameStyle.SOURCE.propertyValue,
            resources = listOf(resource(directory, LoggerNameStyle.JVM_CLASS.propertyValue)),
        )

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.canonicalName)
    }

    @Test
    fun `an unknown style in a classpath resource fails the first lookup`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(resources = listOf(resource(directory, "binary")))

        assertNotEquals(0, exit, output)
        assertContains(output, unknownValue("binary"))
        assertFalse(output.contains("name="), output)
    }

    @Test
    fun `resources that disagree on the style fail the first lookup`(@TempDir directory: Path): Unit {
        val first = resource(directory.resolve("first"), LoggerNameStyle.SOURCE.propertyValue)
        val second = resource(directory.resolve("second"), LoggerNameStyle.JVM_CLASS.propertyValue)

        val (exit, output) = probe(resources = listOf(first, second))

        assertNotEquals(0, exit, output)
        assertContains(
            output,
            "java.lang.IllegalStateException: akki: conflicting values for '$LOGGER_NAME_STYLE_PROPERTY' on the " +
                "classpath: 'source' in ${first.resourceUrl()} and 'jvm-class' in ${second.resourceUrl()}",
        )
        assertFalse(output.contains("name="), output)
    }

    @Test
    fun `resources that agree on the style are accepted`(@TempDir directory: Path): Unit {
        val first = resource(directory.resolve("first"), LoggerNameStyle.JVM_CLASS.propertyValue)
        val second = resource(directory.resolve("second"), LoggerNameStyle.JVM_CLASS.propertyValue)

        val (exit, output) = probe(resources = listOf(first, second))

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.name)
    }

    private fun unknownValue(value: String): String =
        "java.lang.IllegalStateException: akki: unknown value '$value' for '$LOGGER_NAME_STYLE_PROPERTY': " +
            "expected '${LoggerNameStyle.SOURCE.propertyValue}' or '${LoggerNameStyle.JVM_CLASS.propertyValue}'"

    private fun Path.resourceUrl(): URL = resolve(AKKI_PROPERTIES_RESOURCE).toRealPath().toFile().toURI().toURL()

    private fun resource(root: Path, style: String): Path {
        root.resolve(AKKI_PROPERTIES_RESOURCE).also { it.parent.createDirectories() }
            .writeText("$LOGGER_NAME_STYLE_PROPERTY=$style\n")
        return root
    }

    private fun probe(property: String? = null, resources: List<Path> = emptyList()): Pair<Int, String> {
        val java = ProcessHandle.current().info().command().orElseThrow()
        val classpath = (resources.map { it.toString() } + System.getProperty("java.class.path"))
            .joinToString(File.pathSeparator)
        val process = ProcessBuilder(
            listOfNotNull(
                java,
                property?.let { "-D$LOGGER_NAME_STYLE_PROPERTY=$it" },
                "-cp",
                classpath,
                NameStyleProbe::class.java.name,
            ),
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
