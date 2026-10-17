// CHECK_BYTECODE_TEXT
// 1 io/akki/Log\.named \(
// 0 \$\$log
package fixture

import io.akki.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val EMPTY = " "

@Suppress("BLANK_LOGGER_NAME")
private fun blank(): Logger = Log.named(EMPTY)

fun box(): String {
    val failure = assertFailsWith<IllegalArgumentException> { blank() }
    assertEquals("akki: a logger name must not be blank: every backend routes and filters on it", failure.message)
    return "OK"
}
