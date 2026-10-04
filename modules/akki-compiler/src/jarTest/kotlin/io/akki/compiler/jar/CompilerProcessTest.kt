package io.akki.compiler.jar

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class CompilerProcessTest {
    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `loads the adapter of the running compiler`(kotlin: String) {
        val adapter = adapterOf(kotlin)

        assertContains(
            supported(kotlin).verbose.output,
            "logging: akki: compiler plugin $akkiVersion starts. Kotlin $kotlin uses ${adapter.implementation}, " +
                "the compiler adapter for Kotlin ${adapter.minVersion} created by ${adapter.packageName}.",
        )
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `generates a logger that records under the name of its class`(kotlin: String) {
        val plain = supported(kotlin).plain

        assertEquals("OK", plain.exitCode, plain.output)
        assertFalse(plain.output.contains("akki:"), plain.output)
        assertTrue(plain.classes.resolve("sample/Service\$\$Log.class").exists(), plain.output)
        assertContains(plain.run(), "AKKI records=[DEBUG sample.Service debug A-1, INFO sample.Service handled A-1]")
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
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
    @MethodSource("checkedKotlin")
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
    @MethodSource("checkedKotlin")
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
    @MethodSource("checkedKotlin")
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
    @MethodSource("checkedKotlin")
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

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `fails the compilation with a diagnostic when the plugin cannot start`(kotlin: String) {
        val reported = "$kotlin-ij261-1"

        for (compilation in unrecognized(kotlin, reported)) {
            assertEquals("COMPILATION_ERROR", compilation.exitCode, compilation.output)
            assertContains(
                compilation.output,
                "error: [AKKI_CANNOT_START] akki: compiler plugin $akkiVersion cannot start. Kotlin '$reported' is " +
                    "not supported. Only releases, their Beta and RC builds and dev builds are recognized.",
            )
            assertFalse(compilation.output.contains("Exception"), compilation.output)
            assertFalse(compilation.classes.exists(), compilation.output)
        }
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `uses in a dev build the adapter of the release it belongs to`(kotlin: String) {
        val release = KotlinToolingVersion(kotlin).run { "$major.$minor.$patch" }
        val reported = "$release-dev-1"
        val adapter = adapterOf(release)

        val compilation = devBuild(kotlin, reported)

        assertEquals("OK", compilation.exitCode, compilation.output)
        assertContains(
            compilation.output,
            "Kotlin $reported uses ${adapter.implementation}, the compiler adapter for Kotlin ${adapter.minVersion} ",
        )
        assertContains(compilation.run(), "AKKI records=[DEBUG sample.Service debug A-1, INFO sample.Service handled A-1]")
    }

    @ParameterizedTest(name = "adapter of Kotlin {0} in Kotlin {1}", allowZeroInvocations = true)
    @MethodSource("mismatchedKotlin")
    fun `fails the compilation clearly when the selected adapter does not link against the compiler`(
        reported: String,
        kotlin: String,
    ) {
        val compilations = mismatched(reported, kotlin)
        val unlinked = "Kotlin $reported is not supported: the compiler plugin does not link against it. " +
            "Kotlin releases up to $latestTestedKotlin are tested."

        val failed = compilations.filter { it.exitCode != "OK" }
        assertTrue(failed.isNotEmpty(), compilations.joinToString("\n") { it.output })
        for (compilation in failed) {
            val atStart = compilation.exitCode == "COMPILATION_ERROR" &&
                "error: [AKKI_CANNOT_START] akki: compiler plugin $akkiVersion cannot start. $unlinked" in
                compilation.output
            val afterStart = "akki: compiler plugin $akkiVersion failed. $unlinked" in compilation.output
            assertTrue(atStart || afterStart, compilation.output)
        }
    }

    private class Supported(
        val verbose: Compilation,
        val plain: Compilation,
        val clipped: Compilation,
        val unknownThreshold: Compilation,
        val staleCore: Compilation,
        val incompleteCore: Compilation,
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
        fun checkedKotlin(): List<String> = io.akki.compiler.jar.checkedKotlin

        @JvmStatic
        fun rejectedKotlin(): List<String> = io.akki.compiler.jar.rejectedKotlin

        @JvmStatic
        fun mismatchedKotlin(): List<Arguments> {
            val candidates = adapters.zipWithNext { previous, next -> previous to next.minVersion } +
                (adapters.first() to io.akki.compiler.jar.checkedKotlin.last())
            return candidates.distinct()
                .filter { (adapter, kotlin) ->
                    val host = classpath("akki.compiler.host.$kotlin")
                    adapter !== adapterOf(kotlin) &&
                        (violations(adapter, host) + linkageFailures(adapter, host)).isNotEmpty()
                }
                .map { (adapter, kotlin) -> Arguments.of(adapter.minVersion, kotlin) }
        }

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
                repeated = process.compile("repeated", service, core, *plugin),
            ).also { process.run() }
        }

        private fun mismatched(reported: String, kotlin: String): List<Compilation> {
            val process = CompilerProcess(kotlin, workspace.resolve("$reported-in-$kotlin"), reportedVersion = reported)
            val stale = process.compile("stale", listOf("Logger.kt", "AkkiVersion.kt"), libraries)
            return listOf(
                process.compile("plain", listOf("Service.kt"), core, *plugin),
                process.compile("clipped", listOf("Service.kt"), core, *plugin, *option("minLevel", "info")),
                process.compile("staleUser", listOf("User.kt"), libraries.plusElement(stale.classes), *plugin),
            ).also { process.run() }
        }

        private fun unrecognized(kotlin: String, reported: String): List<Compilation> {
            val process = CompilerProcess(kotlin, workspace.resolve(reported), reportedVersion = reported)
            return listOf(
                process.compile("plain", listOf("Service.kt"), core, *plugin),
                process.compile("strict", listOf("Service.kt"), core, *plugin, "-Werror"),
            ).also { process.run() }
        }

        private fun devBuild(kotlin: String, reported: String): Compilation {
            val process = CompilerProcess(kotlin, workspace.resolve("$reported-in-$kotlin"), reportedVersion = reported)
            return process.compile("dev", listOf("Service.kt"), core, *plugin, "-verbose").also { process.run() }
        }

        private fun rejected(kotlin: String): List<Compilation> = rejected.computeIfAbsent(kotlin) {
            val process = CompilerProcess(kotlin, workspace.resolve(kotlin))
            List(2) { process.compile("rejected$it", listOf("Service.kt"), core, *plugin) }.also { process.run() }
        }
    }
}
