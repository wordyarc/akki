// CHECK_BYTECODE_TEXT
// 0 io/akki/IntrinsicKt
// 0 \$\$log
// 0 \$\$Log
// FILE: other/Other.kt
package other

import fixture.*

class Derived : Service() {
    fun inherited(): String = forSubclasses()
}

fun viaOtherPackage(): List<String> = listOf(
    publicTopLevel(),
    internalTopLevel(),
    viaPrivateTopLevel(),
    viaMultifilePart(),
    Service().publicMember(),
    Service().internalMember(),
    Service().property,
    Service.fromCompanion(),
    Derived().inherited(),
    Service().viaPrivateMember(),
    Service().viaPrivateFactory(),
)

// FILE: multifile.kt
@file:JvmMultifileClass
@file:JvmName("Facade")
@file:Suppress("NOTHING_TO_INLINE")

package fixture

import io.akki.*

private inline fun multifilePart(): String = log.name

internal inline fun viaMultifilePart(): String = multifilePart()

// FILE: main.kt
@file:Suppress("NOTHING_TO_INLINE")

package fixture

import io.akki.*
import io.akki.test.recordLogs
import kotlin.test.assertEquals

inline fun publicTopLevel(): String = log.name

internal inline fun internalTopLevel(): String = logger().name

private inline fun privateTopLevel(): String = log.name

internal inline fun viaPrivateTopLevel(): String = privateTopLevel()

val inlineGetter: String inline get() = log.name

inline fun withNoinlineDefault(noinline probe: () -> String = { log.name }): String = probe()

open class Service {
    inline fun publicMember(): String = log.name

    internal inline fun internalMember(): String = log.name

    protected inline fun forSubclasses(): String = log.name

    inline val property: String get() = log.name

    private inline fun <reified T> parse(): String {
        log.info { "parsing ${T::class.simpleName}" }
        return log.name
    }

    internal inline fun viaPrivateMember(): String = parse<Int>()

    private inline fun factory(): String = Log.of<Service>().name

    internal inline fun viaPrivateFactory(): String = factory()

    fun viaObjectExpression(): String {
        val probe = object {
            inline fun name(): String = log.name
        }
        return probe.name()
    }

    fun viaLocalClass(): String {
        class Local {
            inline fun name(): String = log.name
        }
        return Local().name()
    }

    companion object {
        inline fun fromCompanion(): String = log.name
    }
}

private class Hidden {
    inline fun exposed(): String = log.name
}

fun box(): String {
    val file = "fixture.Main"
    val service = "fixture.Service"
    val names: List<String>
    val records = recordLogs { names = other.viaOtherPackage() }

    assertEquals(
        listOf(file, file, file, "fixture.Multifile", service, service, service, service, service, service, service),
        names,
    )
    assertEquals(listOf(service to "parsing Int"), records.map { it.name to it.message })
    assertEquals(
        listOf(file, file, service, service, "fixture.Hidden"),
        listOf(
            inlineGetter,
            withNoinlineDefault(),
            Service().viaObjectExpression(),
            Service().viaLocalClass(),
            Hidden().exposed(),
        ),
    )
    return "OK"
}
