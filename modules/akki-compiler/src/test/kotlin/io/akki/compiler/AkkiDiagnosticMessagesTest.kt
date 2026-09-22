package io.akki.compiler

import kotlin.test.Test
import org.jetbrains.kotlin.test.utils.verifyDiagnostics

internal class AkkiDiagnosticMessagesTest {
    @Test
    fun `every diagnostic has a message in the compiler's style`() {
        verifyDiagnostics(AkkiErrors)
    }
}
