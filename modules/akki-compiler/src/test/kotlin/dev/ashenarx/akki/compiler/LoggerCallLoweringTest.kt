package dev.ashenarx.akki.compiler

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.io.TempDir

class LoggerCallLoweringTest {
    @Test
    fun lowersEagerAndLazyCalls(@TempDir temporaryDirectory: Path): Unit {
        val source = temporaryDirectory.resolve("Fixture.kt")
        val output = temporaryDirectory.resolve("classes").createDirectories()
        source.writeText(FIXTURE)

        val compilerOutput = ByteArrayOutputStream()
        val compiler = K2JVMCompiler()
        val arguments = K2JVMCompilerArguments()
        compiler.parseArguments(
            arrayOf(
                "-d",
                output.toString(),
                "-classpath",
                classpath(),
                "-jvm-target",
                "17",
                "-Xplugin=${requiredProperty("akki.compiler.plugin.jar")}",
                source.toString(),
            ),
            arguments,
        )
        val exitCode = compiler.exec(
            PrintingMessageCollector(
                PrintStream(compilerOutput),
                MessageRenderer.PLAIN_RELATIVE_PATHS,
                true,
            ),
            Services.EMPTY,
            arguments,
        )
        assertEquals(ExitCode.OK, exitCode, compilerOutput.toString())

        URLClassLoader(arrayOf(output.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            val result = classLoader
                .loadClass("fixture.FixtureKt")
                .getMethod("box")
                .invoke(null)
            assertEquals(
                "DEBUG,TRACE,INFO,WARN,ERROR,ERROR|receiver,eager-message,cause,fields,cause,fields,lazy-message,variable-message|enabled-1,lazy-6,constant,variable-7",
                result,
            )
        }
    }

    private fun classpath(): String = listOf(
        requiredProperty("akki.core.jar"),
        File(KotlinVersion::class.java.protectionDomain.codeSource.location.toURI()).absolutePath,
    ).joinToString(File.pathSeparator)

    private fun requiredProperty(name: String): String = requireNotNull(System.getProperty(name))

    private companion object {
        val FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.DelicateAkkiApi
            import dev.ashenarx.akki.Level
            import dev.ashenarx.akki.Log
            import dev.ashenarx.akki.LogBackend
            import dev.ashenarx.akki.Sink

            private class Backend : LogBackend {
                val resolutions = mutableListOf<Level>()
                val messages = mutableListOf<String>()

                override fun resolve(name: String, level: Level): Sink? {
                    resolutions += level
                    if (level == Level.DEBUG || level == Level.TRACE) return null
                    return Sink { message, _, _ -> messages += message }
                }
            }

            @OptIn(DelicateAkkiApi::class)
            fun box(): String {
                val backend = Backend()
                val logger = Log.named("fixture")
                val effects = mutableListOf<String>()
                var sequence = 0

                fun mark(effect: String): Int {
                    effects += effect
                    return ++sequence
                }

                fun cause(): Throwable = IllegalStateException("cause-${'$'}{mark("cause")}")
                fun fields(): Map<String, Any?> = mapOf("value" to mark("fields"))
                fun selectedLogger() = logger.also { effects += "receiver" }
                val variableMessage: () -> String = { "variable-${'$'}{mark("variable-message")}" }

                Log.install(backend).use {
                    selectedLogger().debug("disabled-${'$'}{mark("disabled-message")}", cause(), fields())
                    logger.trace(cause(), fields()) { "disabled-${'$'}{mark("disabled-lazy-message")}" }
                    logger.info("enabled-${'$'}{mark("eager-message")}", cause(), fields())
                    logger.warn(cause(), fields()) { "lazy-${'$'}{mark("lazy-message")}" }
                    logger.error("constant")
                    logger.error(message = variableMessage)
                }

                return listOf(
                    backend.resolutions.joinToString(","),
                    effects.joinToString(","),
                    backend.messages.joinToString(","),
                ).joinToString("|")
            }
            """.trimIndent()
    }
}
