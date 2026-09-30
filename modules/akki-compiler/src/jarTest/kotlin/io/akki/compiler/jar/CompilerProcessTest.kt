package io.akki.compiler.jar

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class CompilerProcessTest {
    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `loads the adapter of the running compiler`(kotlin: String) {
        val adapter = adapterOf(kotlin)

        assertContains(
            supported(kotlin).verbose.output,
            "logging: akki: compiler plugin $akkiVersion starts. Kotlin $kotlin uses ${adapter.implementation}, " +
                "the compiler adapter for Kotlin ${adapter.minVersion} created by ${adapter.packageName}.",
        )
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `generates a logger that records under the name of its class`(kotlin: String) {
        val plain = supported(kotlin).plain

        assertEquals("OK", plain.exitCode, plain.output)
        assertFalse(plain.output.contains("akki:"), plain.output)
        assertTrue(plain.classes.resolve("sample/Service\$\$Log.class").exists(), plain.output)
        assertContains(plain.run(), "AKKI records=[DEBUG sample.Service debug A-1, INFO sample.Service handled A-1]")
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `removes the records below the threshold of the command line and reports them`(kotlin: String) {
        val clipped = supported(kotlin).clipped

        assertEquals("OK", clipped.exitCode, clipped.output)
        assertContains(
            clipped.output,
            "Service.kt:12:13: info: [LOGGING_CALL_REMOVED] The 'debug' record is below 'minLevel=info' and is " +
                "removed at compile time.",
        )
        assertContains(clipped.run(), "AKKI records=[INFO sample.Service handled A-1]")
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `rejects an unknown threshold of the command line`(kotlin: String) {
        val unknown = supported(kotlin).unknownThreshold

        assertEquals("COMPILATION_ERROR", unknown.exitCode, unknown.output)
        assertContains(
            unknown.output,
            "nknown value 'verbose' for the Akki option 'minLevel'. " +
                "Expected one of trace, debug, info, warn, error, off.",
        )
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `reports a core of another version`(kotlin: String) {
        val stale = supported(kotlin).staleCore

        assertEquals("COMPILATION_ERROR", stale.exitCode, stale.output)
        assertContains(
            stale.output,
            "error: [INCOMPATIBLE_AKKI_CORE] Akki compiler plugin $akkiVersion requires akki-core $akkiVersion, " +
                "but the compile classpath contains akki-core 0.0.1.",
        )
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `reports a core without a version`(kotlin: String) {
        val incomplete = supported(kotlin).incompleteCore

        assertEquals("COMPILATION_ERROR", incomplete.exitCode, incomplete.output)
        assertContains(
            incomplete.output,
            "error: [INCOMPATIBLE_AKKI_CORE] Akki compiler plugin $akkiVersion found an incompatible akki-core on " +
                "the compile classpath: its version is unknown and",
        )
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `reports a reference to the contextual logger`(kotlin: String) {
        val reference = supported(kotlin).reference

        assertEquals("COMPILATION_ERROR", reference.exitCode, reference.output)
        assertContains(
            reference.output,
            "Reference.kt:5:24: error: [CONTEXTUAL_LOGGER_REFERENCE] Callable references to 'log' are not supported.",
        )
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun `keeps nothing of the earlier compilations of its process`(kotlin: String) {
        val repeated = supported(kotlin).repeated

        assertEquals("OK", repeated.exitCode, repeated.output)
        assertFalse(repeated.output.contains("LOGGING_CALL_REMOVED"), repeated.output)
        assertFalse(repeated.output.contains("INCOMPATIBLE_AKKI_CORE"), repeated.output)
        assertTrue(repeated.classes.resolve("sample/Service\$\$Log.class").exists(), repeated.output)
        assertContains(repeated.run(), "AKKI records=[DEBUG sample.Service debug A-1, INFO sample.Service handled A-1]")
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("rejectedKotlin")
    fun `refuses to start in a compiler older than every adapter`(kotlin: String) {
        for (compilation in rejected(kotlin)) {
            assertEquals("COMPILATION_ERROR", compilation.exitCode, compilation.output)
            assertContains(
                compilation.output,
                "error: akki: compiler plugin $akkiVersion cannot start. Kotlin $kotlin is not supported. " +
                    "The oldest supported version is ${adapters.first().minVersion}.",
            )
            assertFalse(compilation.output.contains("Exception"), compilation.output)
            assertFalse(compilation.classes.exists(), compilation.output)
        }
    }

    private class Supported(
        val verbose: Compilation,
        val plain: Compilation,
        val clipped: Compilation,
        val unknownThreshold: Compilation,
        val staleCore: Compilation,
        val incompleteCore: Compilation,
        val reference: Compilation,
        val repeated: Compilation,
    )

    companion object {
        @TempDir(cleanup = CleanupMode.ON_SUCCESS)
        @JvmStatic
        lateinit var workspace: Path

        private val core: List<Path> = classpath("akki.core.classpath")

        private val libraries: List<Path> = core.filterNot { it.fileName.toString().startsWith("akki-") }

        private val supported = ConcurrentHashMap<String, Supported>()

        private val rejected = ConcurrentHashMap<String, List<Compilation>>()

        @JvmStatic
        fun testedKotlin(): List<String> = io.akki.compiler.jar.testedKotlin

        @JvmStatic
        fun rejectedKotlin(): List<String> = io.akki.compiler.jar.rejectedKotlin

        private fun Compilation.run(): String = run("sample.ServiceKt", core)

        private fun supported(kotlin: String): Supported = supported.computeIfAbsent(kotlin) {
            val process = CompilerProcess(kotlin, workspace.resolve(kotlin))
            val service = listOf("Service.kt")
            val user = listOf("User.kt")
            val stale = process.compile("stale", listOf("Logger.kt", "AkkiVersion.kt"), libraries)
            val incomplete = process.compile("incomplete", listOf("Logger.kt"), libraries)
            val withStale = libraries.plusElement(stale.classes)
            val withIncomplete = libraries.plusElement(incomplete.classes)
            Supported(
                verbose = process.compile("verbose", service, core, *plugin, "-verbose"),
                plain = process.compile("plain", service, core, *plugin),
                clipped = process.compile("clipped", service, core, *plugin, *option("minLevel", "info")),
                unknownThreshold = process.compile("unknown", service, core, *plugin, *option("minLevel", "verbose")),
                staleCore = process.compile("staleUser", user, withStale, *plugin),
                incompleteCore = process.compile("incompleteUser", user, withIncomplete, *plugin),
                reference = process.compile("reference", listOf("Reference.kt"), core, *plugin),
                repeated = process.compile("repeated", service, core, *plugin),
            ).also { process.run() }
        }

        private fun rejected(kotlin: String): List<Compilation> = rejected.computeIfAbsent(kotlin) {
            val process = CompilerProcess(kotlin, workspace.resolve(kotlin))
            List(2) { process.compile("rejected$it", listOf("Service.kt"), core, *plugin) }.also { process.run() }
        }
    }
}
