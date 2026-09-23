// CHECK_BYTECODE_TEXT
// 1 io/akki/Log\.named \(
// 0 io/akki/internal/LogRegistry\.of
package fixture

import io.akki.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val EMPTY = " "

@Suppress("BLANK_LOG_NAME")
private fun blank(): Logger = Log.named(EMPTY)

fun box(): String {
    val failure = assertFailsWith<IllegalArgumentException> { blank() }
    assertEquals("akki: a logger name must not be blank, it is what every backend routes and filters on", failure.message)
    return "OK"
}
