// CHECK_BYTECODE_TEXT
// 0 io/akki/IntrinsicKt
// FILE: caller/Caller.kt
package caller

import fixture.Service

fun viaAnotherPackage(): String = Service().handle()

// FILE: main.kt
package fixture

import io.akki.*
import io.akki.test.recordLogs
import kotlin.test.assertEquals

class Service {
    private inline fun <reified T> parse(): String {
        log.info { "parsing ${T::class.simpleName}" }
        return log.name
    }

    fun handle(): String = parse<Int>()

    inner class Inner {
        fun handle(): String = parse<Long>()
    }

    val lambda: () -> String = { parse<Short>() }

    companion object {
        fun viaCompanion(service: Service): String = service.parse<String>()
    }
}

private inline fun <reified T> fileLocal(): String = log.name + ":" + T::class.simpleName

class Other {
    fun call(): String = fileLocal<Byte>()
}

private class Hidden {
    @Suppress("NOTHING_TO_INLINE")
    inline fun exposed(): String = log.name
}

fun box(): String {
    val records = recordLogs {
        assertEquals("fixture.Service", Service().handle())
        assertEquals("fixture.Service", Service().Inner().handle())
        assertEquals("fixture.Service", Service().lambda())
        assertEquals("fixture.Service", Service.viaCompanion(Service()))
        assertEquals("fixture.Service", caller.viaAnotherPackage())
    }
    assertEquals(List(5) { "fixture.Service" }, records.map { it.name })
    assertEquals(listOf("parsing Int", "parsing Long", "parsing Short", "parsing String", "parsing Int"), records.map { it.message })
    assertEquals("fixture.Main:Byte", Other().call())
    assertEquals("fixture.Hidden", Hidden().exposed())
    return "OK"
}
