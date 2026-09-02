package dev.ashenarx.akki.compiler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services

internal class Compilation(val classes: Path, val output: String)

internal object FixtureCompiler {
    private const val PLUGIN_ID = "dev.ashenarx.akki"

    fun compile(
        directory: Path,
        fixture: String,
        plugin: Boolean = true,
        enabled: Boolean = true,
        classpath: String = property("akki.fixture.classpath"),
    ): Compilation {
        val (compilation, exitCode) = run(directory, fixture, plugin, enabled, classpath)
        assertEquals(ExitCode.OK, exitCode, compilation.output)
        return compilation
    }

    fun compileExpectingFailure(
        directory: Path,
        fixture: String,
        classpath: String = property("akki.fixture.classpath"),
    ): String {
        val (compilation, exitCode) = run(directory, fixture, plugin = true, enabled = true, classpath = classpath)
        assertNotEquals(ExitCode.OK, exitCode, compilation.output)
        return compilation.output
    }

    fun box(directory: Path, fixture: String, plugin: Boolean = true, enabled: Boolean = true): String =
        compile(directory, fixture, plugin, enabled).invoke("fixture.FixtureKt", "box")

    fun Compilation.invoke(className: String, method: String): String =
        URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            classLoader.loadClass(className).getMethod(method).invoke(null) as String
        }

    fun Compilation.constantPool(className: String): List<String> {
        val bytes = classes.resolve("${className.replace('.', '/')}.class").toFile().readBytes()
        return Regex("[\\w$/.-]{4,}").findAll(String(bytes, Charsets.ISO_8859_1)).map { it.value }.toList()
    }

    private fun run(
        directory: Path,
        fixture: String,
        plugin: Boolean,
        enabled: Boolean,
        classpath: String,
    ): Pair<Compilation, ExitCode> {
        val source = directory.createDirectories().resolve("Fixture.kt")
        val classes = directory.resolve("classes").createDirectories()
        source.writeText(fixture)

        val pluginArguments = if (!plugin) {
            emptyList()
        } else {
            listOf("-Xplugin=${property("akki.compiler.plugin.jar")}", "-P", "plugin:$PLUGIN_ID:enabled=$enabled")
        }
        val arguments = K2JVMCompilerArguments()
        val compiler = K2JVMCompiler()
        compiler.parseArguments(
            (
                listOf(
                    "-d", classes.toString(),
                    "-classpath", classpath,
                    "-jvm-target", property("akki.jvm.target"),
                ) + pluginArguments + source.toString()
                ).toTypedArray(),
            arguments,
        )
        val output = ByteArrayOutputStream()
        val exitCode = compiler.exec(
            PrintingMessageCollector(PrintStream(output), MessageRenderer.PLAIN_RELATIVE_PATHS, true),
            Services.EMPTY,
            arguments,
        )
        return Compilation(classes, output.toString()) to exitCode
    }

    private fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }
}
