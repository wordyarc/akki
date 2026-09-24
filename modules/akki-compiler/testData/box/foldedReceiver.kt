// WITH_HELPERS
// TREAT_AS_ONE_FILE
// CHECK_BYTECODE_TEXT
// 1 io/akki/Log\.named \(
// 2 GETSTATIC fixture/FoldedReceiverKt\.\$\$log\$0
package fixture

import helpers.Effects
import io.akki.*
import kotlin.test.assertEquals

private val effects = Effects()
private val failure = IllegalStateException("receiver failed")

private fun throwingLog(): Log {
    effects.mark("throwing")
    throw failure
}

fun box(): String {
    effects.mark("logger=" + effects.mark("selected", Log).named("receiver-audit").name)
    try {
        throwingLog().named("receiver-audit")
        effects.mark("not-thrown")
    } catch (caught: IllegalStateException) {
        effects.mark("caught=" + (caught === failure))
    }
    assertEquals("selected,logger=receiver-audit,throwing,caught=true", effects.toString())
    return "OK"
}
