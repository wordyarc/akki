package io.akki

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.internal.BACKEND_PROPERTY
import io.akki.internal.LOGGER_NAME_STYLE_PROPERTY
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class JvmBackendPropertyTest {
    @Test
    fun `the system property selects a declared backend`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(property = SECOND, resources = listOf(directory.declareProbeBackends()))

        assertEquals(0, exit, output)
        assertContains(output, "backend=$SECOND")
        assertFalse(output.contains("multiple backends"), output)
    }

    @Test
    fun `a classpath resource selects a declared backend`(@TempDir directory: Path): Unit {
        val resources = listOf(directory.declareProbeBackends().akkiProperties(BACKEND_PROPERTY to SECOND))

        val (exit, output) = probe(resources = resources)

        assertEquals(0, exit, output)
        assertContains(output, "backend=$SECOND")
    }

    @Test
    fun `the system property overrides a classpath resource`(@TempDir directory: Path): Unit {
        val resources = listOf(directory.declareProbeBackends().akkiProperties(BACKEND_PROPERTY to SECOND))

        val (exit, output) = probe(property = FIRST, resources = resources)

        assertEquals(0, exit, output)
        assertContains(output, "backend=$FIRST")
    }

    @Test
    fun `an empty system property restores the automatic selection`(@TempDir directory: Path): Unit {
        val resources = listOf(directory.declareProbeBackends().akkiProperties(BACKEND_PROPERTY to SECOND))

        val (exit, output) = probe(property = "", resources = resources)

        assertEquals(0, exit, output)
        assertContains(output, "akki: multiple backends on the classpath, using $FIRST: $FIRST, $SECOND")
        assertContains(output, "backend=$FIRST")
    }

    @Test
    fun `an unknown backend is reported and the backend is selected automatically`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(property = "acme.Missing", resources = listOf(directory.declareProbeBackends()))

        assertEquals(0, exit, output)
        assertContains(
            output,
            "akki: the backend 'acme.Missing' set by the system property '$BACKEND_PROPERTY' was not found: " +
                "the classpath declares $FIRST, $SECOND; selecting the backend automatically.\n" +
                "akki: multiple backends on the classpath, using $FIRST: $FIRST, $SECOND",
        )
        assertContains(output, "backend=$FIRST")
    }

    @Test
    fun `an unknown backend in a classpath resource names the resource`(@TempDir directory: Path): Unit {
        val resources = listOf(directory.declareProbeBackends().akkiProperties(BACKEND_PROPERTY to "acme.Missing"))

        val (exit, output) = probe(resources = resources)

        assertEquals(0, exit, output)
        assertContains(
            output,
            "akki: the backend 'acme.Missing' set by '$BACKEND_PROPERTY' in ${directory.akkiPropertiesUrl()} " +
                "was not found: the classpath declares $FIRST, $SECOND; selecting the backend automatically.",
        )
        assertContains(output, "backend=$FIRST")
    }

    @Test
    fun `an unknown property in a classpath resource is reported and ignored`(@TempDir directory: Path): Unit {
        val resources = listOf(directory.declareProbeBackends().akkiProperties("io.akki.backned" to SECOND))

        val (exit, output) = probe(resources = resources)

        assertEquals(0, exit, output)
        assertContains(
            output,
            "akki: unknown property 'io.akki.backned' in ${directory.akkiPropertiesUrl()}: " +
                "expected '$LOGGER_NAME_STYLE_PROPERTY', '$BACKEND_PROPERTY' " +
                "or a backend setting 'io.akki.<backend>.<name>'; ignoring it",
        )
        assertContains(output, "name=" + BackendProbe.Owner.Nested::class.java.canonicalName)
        assertContains(output, "backend=$FIRST")
    }

    @Test
    fun `a backend setting in a classpath resource is left to its backend`(@TempDir directory: Path): Unit {
        val resources = listOf(
            directory.declareProbeBackends().akkiProperties(BACKEND_PROPERTY to SECOND, "io.akki.acme.setting" to "x"),
        )

        val (exit, output) = probe(resources = resources)

        assertEquals(0, exit, output)
        assertContains(output, "backend=$SECOND")
    }

    @Test
    fun `resources that disagree on the backend are reported and ignored`(@TempDir directory: Path): Unit {
        val first = directory.resolve("first").akkiProperties(BACKEND_PROPERTY to SECOND)
        val second = directory.resolve("second").akkiProperties(BACKEND_PROPERTY to FIRST)

        val (exit, output) = probe(resources = listOf(directory.declareProbeBackends(), first, second))

        assertEquals(0, exit, output)
        assertContains(
            output,
            "akki: conflicting values for '$BACKEND_PROPERTY' on the classpath: " +
                "'$SECOND' in ${first.akkiPropertiesUrl()} and '$FIRST' in ${second.akkiPropertiesUrl()}; " +
                "ignoring the property, set the system property '$BACKEND_PROPERTY' to choose a value",
        )
        assertContains(output, "akki: multiple backends on the classpath, using $FIRST: $FIRST, $SECOND")
        assertContains(output, "backend=$FIRST")
    }

    @Test
    fun `a system property settles resources that disagree on the backend`(@TempDir directory: Path): Unit {
        val first = directory.resolve("first").akkiProperties(BACKEND_PROPERTY to FIRST)
        val second = directory.resolve("second").akkiProperties(BACKEND_PROPERTY to SECOND)

        val (exit, output) = probe(property = SECOND, resources = listOf(directory.declareProbeBackends(), first, second))

        assertEquals(0, exit, output)
        assertFalse(output.contains("conflicting"), output)
        assertContains(output, "backend=$SECOND")
    }

    @Test
    fun `one resource configures the style and the backend`(@TempDir directory: Path): Unit {
        val resources = listOf(
            directory.declareProbeBackends()
                .akkiProperties(LOGGER_NAME_STYLE_PROPERTY to "jvm-class", BACKEND_PROPERTY to SECOND),
        )

        val (exit, output) = probe(resources = resources)

        assertEquals(0, exit, output)
        assertContains(output, "name=" + BackendProbe.Owner.Nested::class.java.name)
        assertContains(output, "backend=$SECOND")
    }

    private fun Path.declareProbeBackends(): Path {
        resolve("META-INF/services/${LogBackend::class.java.name}").also { it.parent.createDirectories() }
            .writeText("$FIRST\n$SECOND\n")
        return this
    }

    private fun probe(property: String? = null, resources: List<Path> = emptyList()): Pair<Int, String> =
        probe(
            BackendProbe::class.java,
            properties = listOfNotNull(property?.let { BACKEND_PROPERTY to it }).toMap(),
            resources = resources,
        )

    private companion object {
        val FIRST: String = FirstProbeBackend::class.java.name
        val SECOND: String = SecondProbeBackend::class.java.name
    }
}

object BackendProbe {
    class Owner {
        class Nested
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("entered main")
        println("name=" + Log.of<Owner.Nested>().name)
        Log.named("probe").info("record")
    }
}

class FirstProbeBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding {
        println("backend=${javaClass.name}")
        return LoggerBinding { null }
    }
}

class SecondProbeBackend : LogBackend {
    override fun bind(loggerName: String): LoggerBinding {
        println("backend=${javaClass.name}")
        return LoggerBinding { null }
    }
}
