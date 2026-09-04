package dev.ashenarx.akki.compiler

import java.nio.file.Path
import dev.ashenarx.akki.compiler.FixtureCompiler.invoke
import dev.ashenarx.akki.compiler.FixtureCompiler.references
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun inlinesLazyMessagesInsteadOfAllocatingOrCallingThem(@TempDir directory: Path) {
        val references = FixtureCompiler.compile(directory, LAMBDA_FIXTURE).references("fixture.FixtureKt")
        assertFalse(references.any { it.contains("Function0") }, references.toString())
        assertFalse(references.any { it.contains("box\$lambda") }, references.toString())
    }

    @Test
    fun leavesUnresolvableCallSitesAlone(@TempDir directory: Path) {
        assertEquals("via-type-parameter", FixtureCompiler.box(directory, BAILOUT_FIXTURE))
    }

    @Test
    fun reportsExactCallerLocation(@TempDir directory: Path) {
        val line = LOCATION_FIXTURE.lines().indexOfFirst { it.contains(""".info("located")""") } + 1
        assertEquals("fixture.FixtureKt|box|$line", FixtureCompiler.box(directory, LOCATION_FIXTURE))
    }

    @Test
    fun rejectsOverridingLevelMethods(@TempDir directory: Path) {
        val failure = FixtureCompiler.compileExpectingFailure(directory, OVERRIDE_FIXTURE)
        assertContains(failure, "'info' overrides nothing")
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
        val parts = FixtureCompiler.box(directory, fixture, plugin, enabled).split("|")
        return Result(parts[0], parts[1], parts[2])
    }

    private companion object {
        const val PLUGIN_ID = "dev.ashenarx.akki"
        const val DOLLAR = "$"

        val PREAMBLE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

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

            private fun <T : () -> String> viaTypeParameter(logger: Logger, message: T) {
                logger.info(message = message)
            }

            fun box(): String {
                val effects = mutableListOf<String>()
                viaTypeParameter(Recording(effects)) { "via-type-parameter" }
                return effects.joinToString(",")
            }
            """.trimIndent()

        val LOCATION_FIXTURE: String =
            """
            package fixture

            import ch.qos.logback.classic.Level as LogbackLevel
            import ch.qos.logback.classic.LoggerContext
            import ch.qos.logback.classic.spi.ILoggingEvent
            import ch.qos.logback.core.AppenderBase
            import dev.ashenarx.akki.*
            import dev.ashenarx.akki.slf4j.Slf4jBackend
            import org.slf4j.LoggerFactory

            private class Capturing : AppenderBase<ILoggingEvent>() {
                val callers = mutableListOf<StackTraceElement>()

                override fun append(event: ILoggingEvent) {
                    callers += event.callerData.first()
                }
            }

            @OptIn(DelicateAkkiApi::class)
            fun box(): String {
                val context = LoggerFactory.getILoggerFactory() as LoggerContext
                context.reset()
                val appender = Capturing().also { it.context = context; it.start() }
                context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).apply {
                    level = LogbackLevel.TRACE
                    addAppender(appender)
                }
                Log.install(Slf4jBackend).use {
                    Log.named("caller").info("located")
                }
                val caller = appender.callers.single()
                return listOf(caller.className, caller.methodName, caller.lineNumber).joinToString("|")
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
