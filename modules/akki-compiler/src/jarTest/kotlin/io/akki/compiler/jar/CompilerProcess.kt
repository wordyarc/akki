package io.akki.compiler.jar

import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class Compilation(private val directory: Path) {
    val classes: Path
        get() = directory.resolve("classes")

    val exitCode: String
        get() = directory.resolve("exit").readText()

    val output: String
        get() = directory.resolve("output").readText()

    fun run(mainClass: String, classpath: List<Path>): String = java(listOf(classes) + classpath, mainClass)
}

internal class CompilerProcess(private val kotlin: String, private val directory: Path) {
    private val compilations = mutableListOf<Path>()

    fun compile(name: String, sources: List<String>, classpath: List<Path>, vararg options: String): Compilation {
        val compilation = directory.resolve(name)
        val files = sources.map { source ->
            val text = requireNotNull(javaClass.getResource("/sources/$source")) { "no source $source" }.readText()
            compilation.resolve("sources").createDirectories().resolve(source).apply { writeText(text) }
        }
        val arguments = listOf(
            "-no-stdlib",
            "-no-reflect",
            "-jvm-target",
            property("akki.jvm.target"),
            "-Xrender-internal-diagnostic-names",
            "-d",
            compilation.resolve("classes").toString(),
            "-cp",
            classpath.joinToString(File.pathSeparator),
        )
        compilation.resolve("arguments").writeLines(arguments + options + files.map(Path::toString))
        compilations.add(compilation)
        return Compilation(compilation)
    }

    fun run() {
        val driver = classpath("akki.compiler.driver") + classpath("akki.compiler.host.$kotlin")
        val output = java(driver, "io.akki.compiler.host.CompilerHost", compilations.map(Path::toString))
        assertEquals("host $kotlin", output.lineSequence().first(), output)
    }
}

internal val plugin: Array<String> = arrayOf("-Xplugin=$compilerJar")

internal fun option(name: String, value: String): Array<String> = arrayOf("-P", "plugin:io.akki:$name=$value")

private fun java(classpath: List<Path>, mainClass: String, arguments: List<String> = emptyList()): String {
    val java = Path.of(property("java.home"), "bin", "java").toString()
    val command = listOf(java, "-cp", classpath.joinToString(File.pathSeparator), mainClass) + arguments
    val output = File.createTempFile("process", ".log")
    val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output).start()
    val finished = process.waitFor(5, TimeUnit.MINUTES)
    if (!finished) process.destroyForcibly().waitFor()
    val text = output.readText().also { output.delete() }
    assertTrue(finished, "$mainClass did not finish:\n$text")
    assertEquals(0, process.exitValue(), "$mainClass failed:\n$text")
    return text
}
