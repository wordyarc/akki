package dev.ashenarx.akki.compiler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.FieldInsnNode
import org.jetbrains.org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode

internal class Compilation(val classes: Path, val output: String, val exitCode: ExitCode) {
    fun invoke(className: String = BOX_CLASS, method: String = BOX_METHOD): String =
        URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            classLoader.loadClass(className).getMethod(method).invoke(null) as String
        }

    fun references(className: String): List<String> {
        val node = ClassNode()
        val bytes = classes.resolve("${className.replace('.', '/')}.class").toFile().readBytes()
        ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES)
        return buildList {
            node.fields.forEach { add(it.name) }
            node.methods.forEach { method ->
                add(method.name)
                add(method.desc)
                method.instructions.forEach { instruction ->
                    when (instruction) {
                        is FieldInsnNode -> add("${instruction.owner}.${instruction.name}")
                        is MethodInsnNode -> add("${instruction.owner}.${instruction.name}")
                        is TypeInsnNode -> add(instruction.desc)
                        is InvokeDynamicInsnNode -> {
                            add(instruction.desc)
                            instruction.bsmArgs.forEach { add(it.toString()) }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private companion object {
        const val BOX_CLASS: String = "fixture.FixtureKt"
        const val BOX_METHOD: String = "box"
    }
}

internal object FixtureCompiler {
    val defaultClasspath: String = property("akki.fixture.classpath")

    fun compile(directory: Path, source: String, plugin: Plugin, classpath: String): Compilation {
        val file = directory.createDirectories().resolve("Fixture.kt")
        val classes = directory.resolve("classes").createDirectories()
        file.writeText(source)

        val arguments = K2JVMCompilerArguments()
        val compiler = K2JVMCompiler()
        compiler.parseArguments(
            (
                listOf(
                    "-d", classes.toString(),
                    "-classpath", classpath,
                    "-jvm-target", property("akki.jvm.target"),
                    "-Xverify-ir=error",
                    "-Xverify-ir-visibility",
                ) + plugin.arguments() + file.toString()
                ).toTypedArray(),
            arguments,
        )
        val output = ByteArrayOutputStream()
        val exitCode = compiler.exec(
            PrintingMessageCollector(PrintStream(output), MessageRenderer.PLAIN_RELATIVE_PATHS, true),
            Services.EMPTY,
            arguments,
        )
        return Compilation(classes, output.toString(), exitCode)
    }

    private fun Plugin.arguments(): List<String> = when (this) {
        Plugin.Absent -> emptyList()
        else -> listOf(
            "-Xplugin=${property("akki.compiler.plugin.jar")}",
            "-P", "plugin:${AkkiNames.PLUGIN_ID}:enabled=${this == Plugin.Enabled}",
        )
    }

    private fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }
}

internal enum class Plugin {
    Enabled,
    Disabled,
    Absent,
    ;

    val directory: String get() = name.lowercase()
}
