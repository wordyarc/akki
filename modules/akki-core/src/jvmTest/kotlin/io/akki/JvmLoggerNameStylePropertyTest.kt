@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.InternalAkkiApi
import io.akki.internal.LOGGER_NAME_STYLE_PROPERTY
import io.akki.internal.LoggerNameStyle
import java.nio.file.Path
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
        assertContains(output, unknownValue("binary", "the system property '$LOGGER_NAME_STYLE_PROPERTY'"))
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
        val (exit, output) = probe(resources = listOf(directory.styleResource(LoggerNameStyle.JVM_CLASS)))

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.name)
    }

    @Test
    fun `the system property overrides a classpath resource`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(
            property = LoggerNameStyle.SOURCE.propertyValue,
            resources = listOf(directory.styleResource(LoggerNameStyle.JVM_CLASS)),
        )

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.canonicalName)
    }

    @Test
    fun `an empty system property restores the default style`(@TempDir directory: Path): Unit {
        val resources = listOf(directory.styleResource(LoggerNameStyle.JVM_CLASS))

        val (exit, output) = probe(property = "", resources = resources)

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.canonicalName)
    }

    @Test
    fun `an unknown style in a classpath resource fails the first lookup`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(resources = listOf(directory.akkiProperties(LOGGER_NAME_STYLE_PROPERTY to "binary")))

        assertNotEquals(0, exit, output)
        val source = "'$LOGGER_NAME_STYLE_PROPERTY' in ${directory.akkiPropertiesUrl()}"
        assertContains(output, unknownValue("binary", source))
        assertFalse(output.contains("name="), output)
    }

    @Test
    fun `resources that disagree on the style fail the first lookup`(@TempDir directory: Path): Unit {
        val first = directory.resolve("first").styleResource(LoggerNameStyle.SOURCE)
        val second = directory.resolve("second").styleResource(LoggerNameStyle.JVM_CLASS)

        val (exit, output) = probe(resources = listOf(first, second))

        assertNotEquals(0, exit, output)
        assertContains(
            output,
            "io.akki.internal.AkkiConfigurationException: akki: conflicting values for '$LOGGER_NAME_STYLE_PROPERTY' on the " +
                "classpath: 'source' in ${first.akkiPropertiesUrl()} and 'jvm-class' in ${second.akkiPropertiesUrl()}",
        )
        assertFalse(output.contains("name="), output)
    }

    @Test
    fun `resources that agree on the style are accepted`(@TempDir directory: Path): Unit {
        val first = directory.resolve("first").styleResource(LoggerNameStyle.JVM_CLASS)
        val second = directory.resolve("second").styleResource(LoggerNameStyle.JVM_CLASS)

        val (exit, output) = probe(resources = listOf(first, second))

        assertEquals(0, exit, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.name)
    }

    private fun unknownValue(value: String, source: String): String =
        "io.akki.internal.AkkiConfigurationException: akki: unknown value '$value' set by $source: " +
            "expected '${LoggerNameStyle.SOURCE.propertyValue}' or '${LoggerNameStyle.JVM_CLASS.propertyValue}'"

    private fun Path.styleResource(style: LoggerNameStyle): Path =
        akkiProperties(LOGGER_NAME_STYLE_PROPERTY to style.propertyValue)

    private fun probe(property: String? = null, resources: List<Path> = emptyList()): Pair<Int, String> =
        probe(
            NameStyleProbe::class.java,
            properties = listOfNotNull(property?.let { LOGGER_NAME_STYLE_PROPERTY to it }).toMap(),
            resources = resources,
        )
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
