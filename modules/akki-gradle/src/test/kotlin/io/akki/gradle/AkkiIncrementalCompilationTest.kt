package io.akki.gradle

import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class AkkiIncrementalCompilationTest {
    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("io.akki.gradle.KotlinVersions#tested")
    fun `updates fields and creates and removes holders incrementally`(kotlin: String, @TempDir directory: Path) {
        prepareProject(directory, kotlin)
        val service = directory.resolve("library/src/main/kotlin/library/ChangingService.kt")
        val original = service.readText()
        val holder = directory.resolve("library/build/classes/kotlin/main/library/ChangingService\$\$Log.class")

        runConsumers(directory).assertConsumers("AKKI owner=before audit=audit.before folded=audit.before")
        assertTrue(holder.exists())
        val repeated = runConsumers(directory)
        repeated.assertUpToDate(*compileTasks)
        assertContains(repeated.output, "Reusing configuration cache.")

        service.writeText(original.replace("\"before\"", "log.name").replace("CATEGORY", "\"audit.after\""))
        val added = runConsumers(directory)
        added.assertIncremental(":library:compileKotlin", "library/src/main/kotlin/library/ChangingService.kt")
        added.assertUpToDate(":plugged:compileKotlin", ":plain:compileKotlin")
        added.assertConsumers("AKKI owner=library.ChangingService audit=audit.after folded=audit.before")
        assertContains(added.output, "Reusing configuration cache.")

        val withoutLogger = original.replace("\"before\"", "\"after\"").replace("Log.named(CATEGORY).name", "\"none\"")
        service.writeText(withoutLogger)
        val removed = runConsumers(directory)
        removed.assertIncremental(":library:compileKotlin", "library/src/main/kotlin/library/ChangingService.kt")
        removed.assertConsumerCompilation("Main.kt")
        removed.assertConsumers("AKKI owner=after audit=none folded=audit.before")
        assertFalse(holder.exists())

        service.writeText(withoutLogger.replace("\"after\"", "log.name"))
        val recreated = runConsumers(directory)
        recreated.assertIncremental(":library:compileKotlin", "library/src/main/kotlin/library/ChangingService.kt")
        recreated.assertConsumerCompilation("Main.kt")
        recreated.assertConsumers("AKKI owner=library.ChangingService audit=none folded=audit.before")
        assertTrue(holder.exists())
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("io.akki.gradle.KotlinVersions#tested")
    fun `removes generated classes when their source is deleted`(kotlin: String, @TempDir directory: Path) {
        prepareProject(directory, kotlin)
        runConsumers(directory)
        val classes = listOf("Removable.class", "Removable\$\$Log.class")
            .map { directory.resolve("library/build/classes/kotlin/main/library/$it") }
        classes.forEach { assertTrue(it.exists(), it.toString()) }

        directory.resolve("library/src/main/kotlin/library/Removable.kt").deleteExisting()
        val removed = runConsumers(directory)

        compileTasks.forEach { removed.assertIncremental(it) }
        classes.forEach { assertFalse(it.exists(), it.toString()) }
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("io.akki.gradle.KotlinVersions#tested")
    fun `updates folded names when a library constant changes`(kotlin: String, @TempDir directory: Path) {
        prepareProject(directory, kotlin)
        runConsumers(directory).assertConsumers("AKKI owner=before audit=audit.before folded=audit.before")

        directory.resolve("library/src/main/kotlin/library/Constants.kt").replace("audit.before", "audit.after")
        val changed = runConsumers(directory)

        changed.assertIncremental(
            ":library:compileKotlin",
            "library/src/main/kotlin/library/Constants.kt",
            "library/src/main/kotlin/library/ChangingService.kt",
        )
        changed.assertConsumerCompilation("ConstantUse.kt")
        changed.assertConsumers("AKKI owner=before audit=audit.after folded=audit.after")
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("io.akki.gradle.KotlinVersions#tested")
    fun `updates inlined logging and regenerated objects in unchanged consumers`(kotlin: String, @TempDir directory: Path) {
        prepareProject(directory, kotlin)
        runConsumers(directory).assertConsumers("AKKI inline=library.Library:one anonymous=caller:one:consumer.Caller")

        directory.resolve("library/src/main/kotlin/library/Library.kt").replace("one", "two")
        val changed = runConsumers(directory)

        changed.assertIncremental(":library:compileKotlin", "library/src/main/kotlin/library/Library.kt")
        changed.assertConsumerCompilation("Caller.kt")
        changed.assertConsumers(
            "AKKI inline=library.Library:two anonymous=caller:two:consumer.Caller",
            "AKKI records=DEBUG library.Library debug two,INFO library.Library info two",
        )
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("io.akki.gradle.KotlinVersions#tested")
    fun `recompiles inline consumers when the library threshold changes`(kotlin: String, @TempDir directory: Path) {
        prepareProject(directory, kotlin)
        val full = runConsumers(directory)
        full.assertConsumers("AKKI records=DEBUG library.Library debug one,INFO library.Library info one")
        assertFalse(full.output.contains("akki: minLevel"), full.output)

        val clipped = runConsumers(directory, "-PlibraryMinLevel=INFO")
        clipped.assertRebuild(":library:compileKotlin")
        clipped.assertConsumerCompilation("Caller.kt")
        clipped.assertConsumers("AKKI records=INFO library.Library info one")
        assertContains(clipped.output, "akki: minLevel=info")

        val repeated = runConsumers(directory, "-PlibraryMinLevel=INFO")
        repeated.assertUpToDate(*compileTasks)
        repeated.assertConsumers("AKKI records=INFO library.Library info one")
        assertContains(repeated.output, "Reusing configuration cache.")

        val restored = runConsumers(directory)
        restored.assertRebuild(":library:compileKotlin")
        restored.assertConsumerCompilation("Caller.kt")
        restored.assertConsumers("AKKI records=DEBUG library.Library debug one,INFO library.Library info one")
        assertFalse(restored.output.contains("akki: minLevel"), restored.output)
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("io.akki.gradle.KotlinVersions#tested")
    fun `reports and recovers from an invalid name in an unchanged consumer`(kotlin: String, @TempDir directory: Path) {
        prepareProject(directory, kotlin)
        directory.resolve("library/src/main/kotlin/library/ChangingService.kt").replace("CATEGORY", "\"library.audit\"")
        runConsumers(directory).assertConsumers("AKKI owner=before audit=library.audit folded=audit.before")
        val constant = directory.resolve("library/src/main/kotlin/library/Constants.kt")

        constant.replace("\"audit.before\"", "\"\"")
        val failed = runKotlinBuild(directory, ":plugged:compileKotlin", expectFailure = true)
        failed.assertIncremental(":library:compileKotlin", "library/src/main/kotlin/library/Constants.kt")
        failed.assertIncremental(
            ":plugged:compileKotlin",
            "plugged/src/main/kotlin/consumer/ConstantUse.kt",
            outcome = TaskOutcome.FAILED,
        )
        val errors = failed.output.lineSequence().filter { it.startsWith("e: ") }.toList()
        assertEquals(1, errors.size, failed.output)
        assertContains(errors.single(), "ConstantUse.kt:7:36 [BLANK_LOGGER_NAME]")

        constant.replace("\"\"", "\"audit.recovered\"")
        val recovered = runConsumers(directory)
        recovered.assertIncremental(":library:compileKotlin", "library/src/main/kotlin/library/Constants.kt")
        recovered.assertConsumerCompilation("ConstantUse.kt")
        recovered.assertConsumers("AKKI owner=before audit=library.audit folded=audit.recovered")
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("io.akki.gradle.KotlinVersions#tested")
    fun `renames an inline source and produces the same classes as a clean build`(kotlin: String, @TempDir directory: Path) {
        prepareProject(directory, kotlin)
        runConsumers(directory).assertConsumers("AKKI inline=library.Library:one anonymous=caller:one:consumer.Caller")
        val source = directory.resolve("library/src/main/kotlin/library/Library.kt")

        source.moveTo(source.resolveSibling("Renamed.kt"))
        val renamed = runConsumers(directory)
        renamed.assertIncremental(":library:compileKotlin", "library/src/main/kotlin/library/Renamed.kt")
        renamed.assertConsumerCompilation("Caller.kt")
        renamed.assertConsumers(
            "AKKI inline=library.Renamed:one anonymous=caller:one:consumer.Caller",
            "AKKI records=DEBUG library.Renamed debug one,INFO library.Renamed info one",
        )
        val incrementalClasses = classFiles(directory)
        assertFalse(incrementalClasses.keys.any { it.substringAfterLast('/').startsWith("LibraryKt") })

        val clean = runConsumers(directory, ":library:clean", ":plugged:clean", ":plain:clean")
        compileTasks.forEach(clean::assertRebuild)
        assertEquals(renamed.output.records(), clean.output.records(), clean.output)
        assertEquals(incrementalClasses, classFiles(directory))
    }

    private fun prepareProject(directory: Path, kotlin: String) {
        publishRepository(directory)
        val fixture = Path.of(requireNotNull(javaClass.getResource("/incremental")).toURI())
        val substitutions = mapOf(
            "@kotlinVersion@" to kotlin,
            "@pluginId@" to PLUGIN_ID,
            "@akkiVersion@" to VERSION,
            "@mavenGroup@" to GROUP,
        )
        for (name in listOf("settings.gradle.kts", "build.gradle.kts", "gradle.properties", "library")) {
            fixture.resolve(name).toFile().copyRecursively(directory.resolve(name).toFile())
        }
        for (consumer in consumers) {
            fixture.resolve("consumer").toFile().copyRecursively(directory.resolve(consumer).toFile())
            directory.resolve("$consumer/build.gradle.kts").replace(
                "@akkiPlugin@",
                if (consumer == "plugged") "id(\"$PLUGIN_ID\")" else "",
            )
        }
        directory.toFile().walkTopDown().filter { it.extension == "kts" }.forEach { script ->
            script.writeText(substitutions.entries.fold(script.readText()) { text, (key, value) -> text.replace(key, value) })
        }
    }

    private fun runConsumers(directory: Path, vararg arguments: String): KotlinBuild =
        runKotlinBuild(directory, *arguments, ":plugged:run", ":plain:run").also {
            it.assertConsumers("AKKI retained=library.Unchanged,consumer.Untouched")
        }

    private fun KotlinBuild.assertConsumers(vararg lines: String) {
        lines.forEach { expected -> assertEquals(2, output.lineSequence().count { it == expected }, output) }
    }

    private fun KotlinBuild.assertConsumerCompilation(vararg files: String) {
        consumers.forEach { consumer ->
            assertIncremental(":$consumer:compileKotlin", *files.map { "$consumer/src/main/kotlin/consumer/$it" }.toTypedArray())
        }
    }

    private fun Path.replace(old: String, new: String) {
        val original = readText()
        assertContains(original, old)
        writeText(original.replace(old, new))
    }

    private fun classFiles(directory: Path): Map<String, List<Byte>> =
        (listOf("library") + consumers).flatMap { module ->
            directory.resolve("$module/build/classes/kotlin/main").toFile().walkTopDown()
                .filter { it.extension == "class" }.toList()
        }.associate { it.relativeTo(directory.toFile()).invariantSeparatorsPath to it.readBytes().toList() }

    private fun String.records(): List<String> = lineSequence().filter { it.startsWith("AKKI ") }.toList()

    private companion object {
        val consumers: List<String> = listOf("plugged", "plain")
        val compileTasks: Array<String> = (listOf("library") + consumers).map { ":$it:compileKotlin" }.toTypedArray()
    }
}
