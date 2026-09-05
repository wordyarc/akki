package dev.ashenarx.akki.compiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class LoggerCallLoweringTest : FixtureTest() {
    @Test
    fun `resolves the sink once per record`() {
        val lowered = effects(box("effects"))

        assertEquals("DEBUG,TRACE,INFO,WARN,ERROR,ERROR", lowered.resolutions)
        assertEquals("enabled-1,lazy-6,constant,variable-7", lowered.messages)
    }

    @Test
    fun `suppresses arguments of disabled levels`() {
        val (lowered, plain) = effectsBothWays("effects")

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
    fun `keeps argument evaluation order`() {
        val (lowered, plain) = effectsBothWays("order")

        assertEquals("cause,fields,message", plain.effects)
        assertEquals(plain.effects, lowered.effects)
        assertEquals(plain.messages, lowered.messages)
    }

    @Test
    fun `defers out of order named arguments`() {
        val (lowered, plain) = effectsBothWays("namedArguments")

        assertEquals("", lowered.effects)
        assertEquals("fields", plain.effects)
        assertEquals(plain.messages, lowered.messages)
    }

    @Test
    fun `keeps lazy messages free of a lambda allocation`() {
        val lowered = compile("lambda").references("fixture.FixtureKt")
        val plain = compile("lambda", Plugin.Absent).references("fixture.FixtureKt")

        assertFalse(plain.any { it.contains("Function0") }, plain.toString())
        assertFalse(lowered.any { it.contains("Function0") }, lowered.toString())
        assertFalse(lowered.any { it.contains("box\$lambda") }, lowered.toString())
        assertLoweringIsTransparent("value=1|counter=1", "lambda")
    }

    @Test
    fun `lowers messages typed by a type parameter`() {
        assertLoweringIsTransparent("via-type-parameter", "typeParameterMessage")
    }

    @Test
    fun `reports the exact caller location`() {
        val line = Fixtures.source("callerLocation")
            .lines()
            .indexOfFirst { it.contains(""".info("located")""") } + 1

        assertEquals("fixture.FixtureKt|box|$line", box("callerLocation"))
    }

    @Test
    fun `needs no import beyond the intrinsic`() {
        assertLoweringIsTransparent("fixture.OrderService", "minimalImport")
    }

    @Test
    fun `rejects overriding level methods`() {
        val failure = compileExpectingFailure("overriddenLevel")

        assertContains(failure, "'info' in 'Logger' is final and cannot be overridden")
    }

    @Test
    fun `routes through the sink a custom logger declares`() {
        assertLoweringIsTransparent(
            "sink:INFO,own:INFO:kept,sink:DEBUG,sink:WARN,own:WARN:lazy,emit:ERROR:[delegate] decorated",
            "customLogger",
        )
    }

    @Test
    fun `keeps the calling declaration without the plugin`() {
        val (className, methodName) = box("callerLocation", Plugin.Absent).split("|")

        assertEquals("fixture.FixtureKt", className)
        assertEquals("box", methodName)
    }

    @Test
    fun `lowers nothing when disabled`() {
        val disabled = effects(box("effects", Plugin.Disabled))
        val plain = effects(box("effects", Plugin.Absent))

        assertEquals(plain.effects, disabled.effects)
    }

    private class Effects(val resolutions: String, val effects: String, val messages: String)

    private fun effects(output: String): Effects {
        val parts = output.split("|")
        return Effects(parts[0], parts[1], parts[2])
    }

    private fun effectsBothWays(fixture: String): Pair<Effects, Effects> {
        val outputs = boxBothWays(fixture)
        return effects(outputs.lowered) to effects(outputs.plain)
    }
}
