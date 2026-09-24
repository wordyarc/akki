// MODULE: library
// FILE: Library.kt
@file:Suppress("NOTHING_TO_INLINE")

package fixture.library

import io.akki.Log
import io.akki.Logger

class Owner {
    class Nested
}

inline fun named(): Logger = Log.named("inline-audit")

inline fun literal(): Logger = Log.of(Owner.Nested::class)

inline fun concrete(): Logger = Log.of<Owner.Nested>()

inline fun <reified T : Any> typed(): Logger = Log.of<T>()

// MODULE: main(library)
// WITH_HELPERS
// FILE: Main.kt
package fixture.consumer

import fixture.library.*
import helpers.runtime
import kotlin.test.assertSame

fun box(): String {
    assertSame(runtime("inline-audit"), named())
    val expected = runtime(Owner.Nested::class)
    assertSame(expected, literal())
    assertSame(expected, concrete())
    assertSame(expected, typed<Owner.Nested>())
    return "OK"
}
