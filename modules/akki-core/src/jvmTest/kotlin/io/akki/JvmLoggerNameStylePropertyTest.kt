@file:OptIn(InternalAkkiApi::class)

package io.akki

import io.akki.InternalAkkiApi
import io.akki.internal.LOGGER_NAME_STYLE_PROPERTY
import io.akki.internal.LoggerNameStyle
import io.akki.internal.settingValue
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class JvmLoggerNameStylePropertyTest {
    @Test
    fun `an unknown style is reported once and the default style names the logger`(): Unit {
        val (exit, output) = probe(property = "binary")

        assertEquals(0, exit, output)
        val reported = unknownValue("binary", "the system property '$LOGGER_NAME_STYLE_PROPERTY'")
        assertEquals(1, output.split(reported).size - 1, output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.canonicalName)
    }

    @Test
    fun `a known style names the logger`(): Unit {
        val (exit, output) = probe(property = LoggerNameStyle.JVM_CLASS.settingValue)

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
            property = LoggerNameStyle.SOURCE.settingValue,
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
    fun `an unknown style in a classpath resource names the resource`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(resources = listOf(directory.akkiProperties(LOGGER_NAME_STYLE_PROPERTY to "binary")))

        assertEquals(0, exit, output)
        val source = "'$LOGGER_NAME_STYLE_PROPERTY' in ${directory.akkiPropertiesUrl()}"
        assertContains(output, unknownValue("binary", source))
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.canonicalName)
    }

    @Test
    fun `resources that disagree on the style are reported and ignored`(@TempDir directory: Path): Unit {
        val first = directory.resolve("first").styleResource(LoggerNameStyle.JVM_CLASS)
        val second = directory.resolve("second").styleResource(LoggerNameStyle.SOURCE)

        val (exit, output) = probe(resources = listOf(first, second))

        assertEquals(0, exit, output)
        assertContains(
            output,
            "akki: conflicting values for '$LOGGER_NAME_STYLE_PROPERTY' on the classpath: " +
                "'jvm-class' in ${first.akkiPropertiesUrl()} and 'source' in ${second.akkiPropertiesUrl()}; " +
                "ignoring the property, set the system property '$LOGGER_NAME_STYLE_PROPERTY' to choose a value",
        )
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.canonicalName)
    }

    @Test
    fun `a system property settles resources that disagree on the style`(@TempDir directory: Path): Unit {
        val first = directory.resolve("first").styleResource(LoggerNameStyle.SOURCE)
        val second = directory.resolve("second").styleResource(LoggerNameStyle.JVM_CLASS)

        val (exit, output) = probe(property = LoggerNameStyle.JVM_CLASS.settingValue, resources = listOf(first, second))

        assertEquals(0, exit, output)
        assertFalse(output.contains("akki:"), output)
        assertContains(output, "name=" + NameStyleProbe.Owner.Nested::class.java.name)
    }

    @Test
    fun `a class that keeps its logger in a static field loads despite resources that disagree`(
        @TempDir directory: Path,
    ): Unit {
        val first = directory.resolve("first").styleResource(LoggerNameStyle.SOURCE)
        val second = directory.resolve("second").styleResource(LoggerNameStyle.JVM_CLASS)

        val (exit, output) = probe(StaticLoggerProbe::class.java, resources = listOf(first, second))

        assertEquals(0, exit, output)
        assertContains(output, "akki: conflicting values for '$LOGGER_NAME_STYLE_PROPERTY'")
        assertContains(output, "name=" + StaticLoggerProbe::class.java.canonicalName)
        assertContains(output, "INFO  ${StaticLoggerProbe::class.java.canonicalName} - record")
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
        "akki: unknown value '$value' set by $source: " +
            "expected '${LoggerNameStyle.SOURCE.settingValue}' or '${LoggerNameStyle.JVM_CLASS.settingValue}'; " +
            "using '${LoggerNameStyle.SOURCE.settingValue}'"

    private fun Path.styleResource(style: LoggerNameStyle): Path =
        akkiProperties(LOGGER_NAME_STYLE_PROPERTY to style.settingValue)

    private fun probe(property: String? = null, resources: List<Path> = emptyList()): Pair<Int, String> =
        probe(
            NameStyleProbe::class.java,
            properties = listOfNotNull(property?.let { LOGGER_NAME_STYLE_PROPERTY to it }).toMap(),
            resources = resources,
        )
}

object StaticLoggerProbe {
    private val log: Logger = Log.of<StaticLoggerProbe>()

    @JvmStatic
    fun main(args: Array<String>) {
        println("name=" + log.name)
        log.info("record")
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
