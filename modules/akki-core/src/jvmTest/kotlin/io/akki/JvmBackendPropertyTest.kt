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
import kotlin.test.assertNotEquals
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
    fun `an unknown backend fails the first record instead of falling back`(@TempDir directory: Path): Unit {
        val (exit, output) = probe(property = "acme.Missing", resources = listOf(directory.declareProbeBackends()))

        assertNotEquals(0, exit, output)
        assertContains(output, "entered main")
        assertContains(
            output,
            "java.lang.IllegalStateException: akki: unknown backend 'acme.Missing' for '$BACKEND_PROPERTY': " +
                "the classpath declares $FIRST, $SECOND",
        )
        assertFalse(output.contains("backend="), output)
    }

    @Test
    fun `resources that disagree on the backend fail the first lookup`(@TempDir directory: Path): Unit {
        val first = directory.resolve("first").akkiProperties(BACKEND_PROPERTY to FIRST)
        val second = directory.resolve("second").akkiProperties(BACKEND_PROPERTY to SECOND)

        val (exit, output) = probe(resources = listOf(directory.declareProbeBackends(), first, second))

        assertNotEquals(0, exit, output)
        assertContains(
            output,
            "java.lang.IllegalStateException: akki: conflicting values for '$BACKEND_PROPERTY' on the classpath: " +
                "'$FIRST' in ${first.akkiPropertiesUrl()} and '$SECOND' in ${second.akkiPropertiesUrl()}",
        )
        assertFalse(output.contains("backend="), output)
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
