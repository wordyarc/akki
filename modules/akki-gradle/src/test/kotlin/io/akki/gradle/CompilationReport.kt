package io.akki.gradle

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CompilationReport(private val text: String) {
    fun assertIncremental(task: String, vararg expectedSources: String) {
        val log = compilationLog(task)
        assertContains(log, INCREMENTAL, text)
        assertFalse(log.any { it.startsWith(REBUILD) || it.startsWith(FALLBACK) }, text)
        val sources = buildSet {
            var inIteration = false
            for (line in log) {
                if (line == "Compile iteration:") {
                    inIteration = true
                } else if (inIteration) {
                    val source = line.substringBefore(" <- ")
                    if (source.endsWith(".kt")) add(source.replace('\\', '/')) else inIteration = false
                }
            }
        }
        assertEquals(expectedSources.toSet(), sources, text)
    }

    fun assertRebuild(task: String) {
        val log = compilationLog(task)
        assertTrue(log.any { it.startsWith(REBUILD) }, text)
        assertFalse(log.any { it == INCREMENTAL || it.startsWith(FALLBACK) }, text)
    }

    private fun compilationLog(task: String): List<String> {
        val lines = text.lines()
        val starts = lines.indices.filter { lines[it] == "Compilation log for task '$task':" }
        assertEquals(1, starts.size, "Expected one compilation log for $task:\n$text")
        return lines.drop(starts.single() + 1)
            .takeWhile {
                !it.startsWith("Task '") && !it.startsWith("Time metrics:") &&
                    !it.startsWith("Compilation log for task '")
            }
            .map(String::trim)
    }

    private companion object {
        const val INCREMENTAL: String = "Incremental compilation completed"
        const val REBUILD: String = "Non-incremental compilation will be performed"
        const val FALLBACK: String = "Falling back to non-incremental compilation"
    }
}
