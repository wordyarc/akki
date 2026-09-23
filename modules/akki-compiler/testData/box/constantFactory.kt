// CHECK_BYTECODE_TEXT
// 4 io/akki/internal/LogRegistry\.forDeclaration
// 2 io/akki/internal/LogRegistry\.of
// 3 io/akki/Log\.of \(
// 2 io/akki/Log\.named \(
package fixture

import io.akki.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class Service {
    class Nested

    companion object
}

@LogName("class-audit")
class Renamed

object Standalone

private const val CONSTANT_AUDIT = "constant-audit"

class Site {
    fun reified(): Logger = Log.of<Service>()

    fun literal(): Logger = Log.of(Service.Nested::class)

    fun companionType(): Logger = Log.of(Service.Companion::class)

    fun renamed(): Logger = Log.of<Renamed>()

    fun standalone(): Logger = Log.of<Standalone>()

    fun audit(): Logger = Log.named("audit")

    fun constant(): Logger = Log.named(CONSTANT_AUDIT)

    fun builtin(): Logger = Log.of<String>()

    fun array(): Logger = Log.of<IntArray>()
}

private fun runtime(type: KClass<*>): Logger = Log.of(type)

private fun agrees(site: String, folded: Logger, runtime: Logger): String =
    if (folded === runtime) "$site=${folded.name}" else "$site=${folded.name}!=${runtime.name}"

fun box(): String {
    val site = Site()
    assertEquals(
        listOf(
            "reified=fixture.Service",
            "literal=fixture.Service.Nested",
            "companion=fixture.Service",
            "renamed=class-audit",
            "standalone=fixture.Standalone",
            "named=audit",
            "constant=constant-audit",
            "builtin=kotlin.String",
            "array=kotlin.IntArray",
        ),
        listOf(
            agrees("reified", site.reified(), runtime(Service::class)),
            agrees("literal", site.literal(), runtime(Service.Nested::class)),
            agrees("companion", site.companionType(), runtime(Service.Companion::class)),
            agrees("renamed", site.renamed(), runtime(Renamed::class)),
            agrees("standalone", site.standalone(), runtime(Standalone::class)),
            agrees("named", site.audit(), Log.named(StringBuilder("audit").toString())),
            agrees("constant", site.constant(), Log.named(StringBuilder("constant-audit").toString())),
            agrees("builtin", site.builtin(), runtime(String::class)),
            agrees("array", site.array(), runtime(IntArray::class)),
        ),
    )
    return "OK"
}
