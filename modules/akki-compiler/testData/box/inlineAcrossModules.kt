// MODULE: library
// FILE: Library.kt
@file:Suppress("NOTHING_TO_INLINE")

package fixture.library

import io.akki.*

open class Owner {
    class Nested

    inline fun member(): Logger = log

    inline val property: Logger get() = log

    inline fun <reified T : Any> reified(): String = log.name + ":" + T::class.simpleName

    inline fun record(message: String) {
        log.info { message }
    }

    protected inline fun forSubclasses(): Logger = log

    companion object {
        inline fun anchored(): Logger = logger()
    }
}

inline fun topLevel(): Logger = log

inline fun named(): Logger = Log.named("inline-audit")

inline fun literal(): Logger = Log.of(Owner.Nested::class)

inline fun concrete(): Logger = Log.of<Owner.Nested>()

inline fun <reified T : Any> typed(): Logger = Log.of<T>()

// MODULE: pluginless(library)
// WITHOUT_PLUGIN
// FILE: Pluginless.kt
package fixture.pluginless

import fixture.library.*
import io.akki.Logger

class Derived : Owner() {
    fun inherited(): Logger = forSubclasses()
}

fun loggers(): List<Logger> = listOf(topLevel(), Owner().member(), Owner().property, Owner.anchored(), Derived().inherited())

// MODULE: main(library, pluginless)
// WITH_HELPERS
// FILE: Main.kt
package fixture.consumer

import fixture.library.*
import helpers.runtime
import io.akki.Logger
import io.akki.test.recordLogs
import kotlin.test.assertEquals
import kotlin.test.assertSame

class Derived : Owner() {
    fun inherited(): Logger = forSubclasses()
}

fun box(): String {
    val file = runtime("fixture.library.Library")
    val owner = runtime(Owner::class)
    val loggers = listOf(topLevel(), Owner().member(), Owner().property, Owner.anchored(), Derived().inherited())
    assertEquals(listOf(file, owner, owner, owner, owner), loggers)
    assertEquals(loggers, fixture.pluginless.loggers())
    assertEquals("fixture.library.Owner:Int", Owner().reified<Int>())
    val records = recordLogs { Owner().record("inlined") }
    assertEquals(listOf("fixture.library.Owner" to "inlined"), records.map { it.name to it.message })

    assertSame(runtime("inline-audit"), named())
    val nested = runtime(Owner.Nested::class)
    assertSame(nested, literal())
    assertSame(nested, concrete())
    assertSame(nested, typed<Owner.Nested>())
    return "OK"
}
