package io.akki.compiler

import kotlin.test.Test
import org.jetbrains.kotlin.test.utils.verifyDiagnostics

internal class AkkiDiagnosticMessagesTest {
    @Test
    fun `diagnostic messages follow compiler conventions`() {
        verifyDiagnostics(AkkiErrors)
    }
}
