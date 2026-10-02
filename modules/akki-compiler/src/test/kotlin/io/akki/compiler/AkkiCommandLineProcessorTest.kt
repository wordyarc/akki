@file:OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class)

package io.akki.compiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

internal class AkkiCommandLineProcessorTest {
    private val processor = AkkiCommandLineProcessor()

    @Test
    fun `declares the threshold as one optional option given at most once`() {
        val option = processor.pluginOptions.single()

        assertEquals("minLevel", option.optionName)
        assertEquals("trace|debug|info|warn|error|off", option.valueDescription)
        assertFalse(option.required)
        assertFalse(option.allowMultipleOccurrences)
    }

    @Test
    fun `stores the threshold under its configuration key`() {
        val configuration = CompilerConfiguration()

        processor.processOption(processor.pluginOptions.single(), "info", configuration)

        assertEquals(MinLevel.INFO, configuration[AkkiConfigurationKeys.MIN_LEVEL])
    }

    @Test
    fun `rejects an unknown threshold and lists valid values`() {
        val failure = assertFailsWith<CliOptionProcessingException> {
            processor.processOption(processor.pluginOptions.single(), "verbose", CompilerConfiguration())
        }

        assertContains(failure.message.orEmpty(), "value 'verbose' for the Akki option 'minLevel'")
        assertContains(failure.message.orEmpty(), "trace, debug, info, warn, error, off")
    }
}
