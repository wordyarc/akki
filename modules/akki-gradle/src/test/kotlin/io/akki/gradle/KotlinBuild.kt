package io.akki.gradle

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

internal fun runKotlinBuild(
    projectDirectory: Path,
    vararg arguments: String,
    expectFailure: Boolean = false,
): KotlinBuild {
    val reports = projectDirectory.resolve("build/reports/kotlin-build")
    check(reports.toFile().deleteRecursively()) { "Could not clear $reports" }
    val runner = gradleRunner(projectDirectory, *arguments)
    val result = if (expectFailure) runner.buildAndFail() else runner.build()
    assertTrue(reports.exists(), result.output)
    val files = reports.listDirectoryEntries("*.txt")
    assertTrue(files.isNotEmpty(), result.output)
    return KotlinBuild(result, CompilationReport(files.sorted().joinToString("\n") { it.readText() }))
}

internal class KotlinBuild(val result: BuildResult, private val report: CompilationReport) {
    val output: String get() = result.output

    fun assertIncremental(task: String, vararg sources: String, outcome: TaskOutcome = TaskOutcome.SUCCESS) {
        assertEquals(outcome, result.task(task)?.outcome, output)
        report.assertIncremental(task, *sources)
    }

    fun assertRebuild(task: String) {
        assertEquals(TaskOutcome.SUCCESS, result.task(task)?.outcome, output)
        report.assertRebuild(task)
    }

    fun assertUpToDate(vararg tasks: String) {
        tasks.forEach { assertEquals(TaskOutcome.UP_TO_DATE, result.task(it)?.outcome, output) }
    }
}
