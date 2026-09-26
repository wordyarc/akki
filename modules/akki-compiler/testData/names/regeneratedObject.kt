// MODULE: library
// FILE: Library.kt
package fixture.library

import io.akki.*

abstract class Handler {
    val logger: Logger = Log.of(javaClass)
}

inline fun <T> capturing(crossinline block: () -> T): Handler = object : Handler() {
    override fun toString(): String = block().toString()
}

inline fun plain(): Handler = object : Handler() {}

// MODULE: main(library)
// FILE: main.kt
package fixture

import fixture.library.*
import io.akki.*
import kotlin.test.assertContains
import kotlin.test.assertSame

class Caller {
    fun viaCapturing(): Handler = capturing { 42 }

    fun viaPlain(): Handler = plain()
}

fun box(): String {
    for (handler in listOf(Caller().viaCapturing(), Caller().viaPlain())) {
        assertContains(handler.javaClass.name, "\$\$inlined\$")
        assertSame(Log.of<Caller>(), handler.logger, handler.javaClass.name)
    }
    return "OK"
}
