package dev.ashenarx.akki.compiler

import kotlin.test.Test
import kotlin.test.assertEquals

internal class LoggerAliasLoweringTest : FixtureTest() {
    @Test
    fun keepsTheLoggerNamesOfTheRemovedAliases() {
        val (lowered, plain) = boxBothWays("alias")

        assertEquals(
            "fixture.Service,fixture.Service,fixture.Service,fixture.Holder,fixture.Fixture",
            plain.substringBefore('|'),
        )
        assertEquals(plain.substringBefore('|'), lowered.substringBefore('|'))
    }

    @Test
    fun replacesThePrivateAliasWithTheStaticField() {
        val (lowered, plain) = boxBothWays("alias")

        assertEquals("journal,INSTANCE+journal", plain.substringAfter('|'))
        assertEquals("\$\$log,\$\$log+INSTANCE", lowered.substringAfter('|'))
    }

    @Test
    fun keepsTheEffectsOfAReceiverItNoLongerNeeds() {
        assertLoweringIsTransparent("receiver,receiver|fixture.Service,fixture.Service", "aliasReceiver")
    }

    @Test
    fun keepsAliasesThatAreVisibleOutsideTheirDeclaration() {
        assertLoweringIsTransparent("journal+shared|fixture.Base,fixture.Service", "visibleAlias")
    }

    @Test
    fun keepsAliasesThatAreReferencedOrReassigned() {
        assertLoweringIsTransparent("reassigned+referenced|fixture.Service,fixture.Service", "irremovableAlias")
    }
}
