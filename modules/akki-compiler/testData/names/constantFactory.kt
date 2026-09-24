// WITH_HELPERS
// TREAT_AS_ONE_FILE
// CHECK_BYTECODE_TEXT
// 3 io/akki/internal/LogRegistry\.forDeclaration
// 2 io/akki/internal/LogRegistry\.of
// 2 io/akki/Log\.of \(
// 0 io/akki/Log\.named \(
package fixture

import helpers.agrees
import helpers.byStyle
import helpers.runtime
import io.akki.*
import kotlin.test.assertEquals

class Service {
    class Nested

    companion object
}

object Standalone

private const val CONSTANT_AUDIT = "constant-audit"

class Site {
    fun reified(): Logger = Log.of<Service>()

    fun literal(): Logger = Log.of(Service.Nested::class)

    fun companionType(): Logger = Log.of(Service.Companion::class)

    fun standalone(): Logger = Log.of<Standalone>()

    fun audit(): Logger = Log.named("audit")

    fun constant(): Logger = Log.named(CONSTANT_AUDIT)

    fun builtin(): Logger = Log.of<String>()

    fun array(): Logger = Log.of<IntArray>()
}

fun box(): String {
    val site = Site()
    assertEquals(
        listOf(
            "fixture.Service",
            byStyle("fixture.Service.Nested", "fixture.Service\$Nested"),
            "fixture.Service",
            "fixture.Standalone",
            "audit",
            "constant-audit",
            byStyle("kotlin.String", "java.lang.String"),
            byStyle("kotlin.IntArray", "[I"),
        ),
        listOf(
            agrees(site.reified(), Service::class),
            agrees(site.literal(), Service.Nested::class),
            agrees(site.companionType(), Service.Companion::class),
            agrees(site.standalone(), Standalone::class),
            agrees(site.audit(), runtime("audit")),
            agrees(site.constant(), runtime("constant-audit")),
            agrees(site.builtin(), String::class),
            agrees(site.array(), IntArray::class),
        ).map { it.name },
    )
    return "OK"
}
