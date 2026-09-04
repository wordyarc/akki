package dev.ashenarx.akki.compiler

import kotlin.test.Test
import kotlin.test.assertEquals

internal class LoggerAliasLoweringTest : FixtureTest() {
    @Test
    fun `keeps the logger names of the removed aliases`() {
        val (lowered, plain) = boxBothWays("alias")

        assertEquals(
            "fixture.Service,fixture.Service,fixture.Service,fixture.Holder,fixture.Fixture",
            plain.substringBefore('|'),
        )
        assertEquals(plain.substringBefore('|'), lowered.substringBefore('|'))
    }

    @Test
    fun `replaces the private alias with the static field`() {
        val (lowered, plain) = boxBothWays("alias")

        assertEquals("journal,INSTANCE+journal", plain.substringAfter('|'))
        assertEquals("\$\$log,\$\$log+INSTANCE", lowered.substringAfter('|'))
    }

    @Test
    fun `keeps the effects of a receiver it no longer needs`() {
        assertLoweringIsTransparent("receiver,receiver|fixture.Service,fixture.Service", "aliasReceiver")
    }

    @Test
    fun `keeps aliases that are visible outside their declaration`() {
        assertLoweringIsTransparent("journal+shared|fixture.Base,fixture.Service", "visibleAlias")
    }

    @Test
    fun `keeps aliases that are referenced or reassigned`() {
        assertLoweringIsTransparent("reassigned+referenced|fixture.Service,fixture.Service", "irremovableAlias")
    }
}
