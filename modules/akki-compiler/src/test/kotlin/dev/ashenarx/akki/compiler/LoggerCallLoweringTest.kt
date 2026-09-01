package dev.ashenarx.akki.compiler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.io.TempDir

class LoggerCallLoweringTest {
    @Test
    fun resolvesSinkOncePerRecord(@TempDir directory: Path) {
        val lowered = run(directory, EFFECTS_FIXTURE, plugin = true)
        assertEquals("DEBUG,TRACE,INFO,WARN,ERROR,ERROR", lowered.resolutions)
        assertEquals("enabled-1,lazy-6,constant,variable-7", lowered.messages)
    }

    @Test
    fun suppressesArgumentsOfDisabledLevels(@TempDir directory: Path) {
        val lowered = run(directory.resolve("lowered"), EFFECTS_FIXTURE, plugin = true)
        val plain = run(directory.resolve("plain"), EFFECTS_FIXTURE, plugin = false)

        assertEquals(
            "receiver,eager-message,cause,fields,cause,fields,lazy-message,variable-message",
            lowered.effects,
        )
        assertEquals(
            "receiver,disabled-message,cause,fields,cause,fields,eager-message,cause,fields," +
                "cause,fields,lazy-message,variable-message",
            plain.effects,
        )
        assertEquals("enabled-6,lazy-11,constant,variable-12", plain.messages)
        assertEquals(plain.resolutions, lowered.resolutions)
    }

    @Test
    fun keepsArgumentEvaluationOrder(@TempDir directory: Path) {
        val lowered = run(directory.resolve("lowered"), ORDER_FIXTURE, plugin = true)
        val plain = run(directory.resolve("plain"), ORDER_FIXTURE, plugin = false)
        assertEquals("cause,fields,message", plain.effects)
        assertEquals(plain.effects, lowered.effects)
        assertEquals(plain.messages, lowered.messages)
    }

    @Test
    fun defersOutOfOrderNamedArguments(@TempDir directory: Path) {
        val lowered = run(directory.resolve("lowered"), NAMED_FIXTURE, plugin = true)
        val plain = run(directory.resolve("plain"), NAMED_FIXTURE, plugin = false)
        assertEquals("", lowered.effects)
        assertEquals("fields", plain.effects)
        assertEquals(plain.messages, lowered.messages)
    }

    @Test
    fun allocatesNoLambdaForLazyCalls(@TempDir directory: Path) {
        val classes = compile(directory, LAMBDA_FIXTURE, plugin = true)
        val constants = constantPool(classes.resolve("fixture/FixtureKt.class"))
        assertFalse(constants.any { it.contains("Function0") }, constants.toString())
        assertTrue(constants.any { it.contains("box\$lambda") }, constants.toString())
    }

    @Test
    fun leavesUnresolvableCallSitesAlone(@TempDir directory: Path) {
        val classes = compile(directory, BAILOUT_FIXTURE, plugin = true)
        assertEquals("via-type-parameter,via-super", invoke(classes, "fixture.FixtureKt", "box"))
    }

    @Test
    fun warnsOnOverriddenLevelMethod(@TempDir directory: Path) {
        val output = ByteArrayOutputStream()
        compile(directory, OVERRIDE_FIXTURE, plugin = true, output = output)
        assertContains(output.toString(), "Logger.info has no effect")
    }

    @Test
    fun lowersNothingWhenDisabled(@TempDir directory: Path) {
        val disabled = run(directory.resolve("disabled"), EFFECTS_FIXTURE, plugin = true, enabled = false)
        val plain = run(directory.resolve("plain"), EFFECTS_FIXTURE, plugin = false)
        assertEquals(plain.effects, disabled.effects)
    }

    private class Result(val resolutions: String, val effects: String, val messages: String)

    private fun run(
        directory: Path,
        fixture: String,
        plugin: Boolean,
        enabled: Boolean = true,
    ): Result {
        val classes = compile(directory, fixture, plugin, enabled)
        val parts = invoke(classes, "fixture.FixtureKt", "box").split("|")
        return Result(parts[0], parts[1], parts[2])
    }

    private fun compile(
        directory: Path,
        fixture: String,
        plugin: Boolean,
        enabled: Boolean = true,
        output: ByteArrayOutputStream = ByteArrayOutputStream(),
    ): Path {
        val source = directory.createDirectories().resolve("Fixture.kt")
        val classes = directory.resolve("classes").createDirectories()
        source.writeText(fixture)

        val pluginArguments = if (!plugin) {
            emptyList()
        } else {
            listOf("-Xplugin=${property("akki.compiler.plugin.jar")}", "-P", "plugin:$PLUGIN_ID:enabled=$enabled")
        }
        val compiler = K2JVMCompiler()
        val arguments = K2JVMCompilerArguments()
        compiler.parseArguments(
            (
                listOf(
                    "-d", classes.toString(),
                    "-classpath", property("akki.fixture.classpath"),
                    "-jvm-target", property("akki.jvm.target"),
                ) + pluginArguments + source.toString()
                ).toTypedArray(),
            arguments,
        )
        val exitCode = compiler.exec(
            PrintingMessageCollector(PrintStream(output), MessageRenderer.PLAIN_RELATIVE_PATHS, true),
            Services.EMPTY,
            arguments,
        )
        assertEquals(ExitCode.OK, exitCode, output.toString())
        return classes
    }

    private fun invoke(classes: Path, className: String, method: String): String =
        URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            classLoader.loadClass(className).getMethod(method).invoke(null) as String
        }

    private fun constantPool(classFile: Path): List<String> {
        val bytes = classFile.toFile().readBytes()
        return Regex("[\\w$/.-]{4,}").findAll(String(bytes, Charsets.ISO_8859_1)).map { it.value }.toList()
    }

    private fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }

    private companion object {
        const val PLUGIN_ID = "dev.ashenarx.akki"
        const val DOLLAR = "$"

        val PREAMBLE: String =
            """
            package fixture

            import dev.ashenarx.akki.DelicateAkkiApi
            import dev.ashenarx.akki.Level
            import dev.ashenarx.akki.Log
            import dev.ashenarx.akki.LogBackend
            import dev.ashenarx.akki.Logger
            import dev.ashenarx.akki.Sink

            private class Backend(private val disabled: Set<Level>) : LogBackend {
                val resolutions = mutableListOf<Level>()
                val messages = mutableListOf<String>()

                override fun resolve(name: String, level: Level): Sink? {
                    resolutions += level
                    if (level in disabled) return null
                    return Sink { message, _, _ -> messages += message }
                }
            }
            """.trimIndent()

        val EFFECTS_FIXTURE: String =
            """
            $PREAMBLE

            @OptIn(DelicateAkkiApi::class)
            fun box(): String {
                val backend = Backend(setOf(Level.DEBUG, Level.TRACE))
                val logger = Log.named("fixture")
                val effects = mutableListOf<String>()
                var sequence = 0

                fun mark(effect: String): Int {
                    effects += effect
                    return ++sequence
                }

                fun cause(): Throwable = IllegalStateException("cause-${DOLLAR}{mark("cause")}")
                fun fields(): Map<String, Any?> = mapOf("value" to mark("fields"))
                fun selectedLogger() = logger.also { effects += "receiver" }
                val variableMessage: () -> String = { "variable-${DOLLAR}{mark("variable-message")}" }

                Log.install(backend).use {
                    selectedLogger().debug("disabled-${DOLLAR}{mark("disabled-message")}", cause(), fields())
                    logger.trace(cause(), fields()) { "disabled-${DOLLAR}{mark("disabled-lazy-message")}" }
                    logger.info("enabled-${DOLLAR}{mark("eager-message")}", cause(), fields())
                    logger.warn(cause(), fields()) { "lazy-${DOLLAR}{mark("lazy-message")}" }
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

        val ORDER_FIXTURE: String =
            """
            $PREAMBLE

            @OptIn(DelicateAkkiApi::class)
            fun box(): String {
                val backend = Backend(emptySet())
                val logger = Log.named("fixture")
                val effects = mutableListOf<String>()

                fun <T> mark(effect: String, value: T): T {
                    effects += effect
                    return value
                }

                Log.install(backend).use {
                    logger.warn(
                        mark("cause", IllegalStateException("boom")),
                        mark("fields", mapOf("k" to 1)),
                    ) { mark("message", "lazy") }
                }

                return listOf(
                    backend.resolutions.joinToString(","),
                    effects.joinToString(","),
                    backend.messages.joinToString(","),
                ).joinToString("|")
            }
            """.trimIndent()

        val NAMED_FIXTURE: String =
            """
            $PREAMBLE

            @OptIn(DelicateAkkiApi::class)
            fun box(): String {
                val backend = Backend(setOf(Level.DEBUG))
                val logger = Log.named("fixture")
                val effects = mutableListOf<String>()

                fun fields(): Map<String, Any?> {
                    effects += "fields"
                    return mapOf("k" to 1)
                }

                Log.install(backend).use {
                    logger.debug(fields = fields(), message = "constant")
                }

                return listOf(
                    backend.resolutions.joinToString(","),
                    effects.joinToString(","),
                    backend.messages.joinToString(","),
                ).joinToString("|")
            }
            """.trimIndent()

        val LAMBDA_FIXTURE: String =
            """
            $PREAMBLE

            @OptIn(DelicateAkkiApi::class)
            fun box(): String {
                val backend = Backend(emptySet())
                var counter = 0
                Log.install(backend).use {
                    Log.named("fixture").info { "value=${DOLLAR}{++counter}" }
                }
                return backend.messages.joinToString(",")
            }
            """.trimIndent()

        val BAILOUT_FIXTURE: String =
            """
            $PREAMBLE

            private class Recording(private val effects: MutableList<String>) : Logger {
                override val name: String = "recording"

                override fun isEnabled(level: Level): Boolean = true

                override fun emit(level: Level, message: String, cause: Throwable?, fields: Map<String, Any?>) {
                    effects += message
                }
            }

            private class Delegating(private val effects: MutableList<String>) : Logger {
                override val name: String = "delegating"

                override fun isEnabled(level: Level): Boolean = true

                override fun emit(level: Level, message: String, cause: Throwable?, fields: Map<String, Any?>) {
                    effects += message
                }

                fun record(message: String) {
                    super.info(message, null, emptyMap())
                }
            }

            private fun <T : () -> String> viaTypeParameter(logger: Logger, message: T) {
                logger.info(message = message)
            }

            fun box(): String {
                val effects = mutableListOf<String>()
                viaTypeParameter(Recording(effects)) { "via-type-parameter" }
                Delegating(effects).record("via-super")
                return effects.joinToString(",")
            }
            """.trimIndent()

        val OVERRIDE_FIXTURE: String =
            """
            $PREAMBLE

            class Custom : Logger {
                override val name: String = "custom"

                override fun isEnabled(level: Level): Boolean = true

                override fun emit(level: Level, message: String, cause: Throwable?, fields: Map<String, Any?>) = Unit

                override fun info(message: String, cause: Throwable?, fields: Map<String, Any?>) = Unit
            }
            """.trimIndent()
    }
}
