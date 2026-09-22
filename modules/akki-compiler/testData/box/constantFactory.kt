// CHECK_BYTECODE_TEXT
// 4 io/akki/internal/LogRegistry\.forDeclaration
// 1 io/akki/internal/LogRegistry\.of
// 2 io/akki/Log\.of \(
// 1 io/akki/Log\.named \(
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

class Site {
    fun reified(): Logger = Log.of<Service>()

    fun literal(): Logger = Log.of(Service.Nested::class)

    fun companionType(): Logger = Log.of(Service.Companion::class)

    fun renamed(): Logger = Log.of<Renamed>()

    fun standalone(): Logger = Log.of<Standalone>()

    fun audit(): Logger = Log.named("audit")

    fun builtin(): Logger = Log.of<String>()
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
            "builtin=kotlin.String",
        ),
        listOf(
            agrees("reified", site.reified(), runtime(Service::class)),
            agrees("literal", site.literal(), runtime(Service.Nested::class)),
            agrees("companion", site.companionType(), runtime(Service.Companion::class)),
            agrees("renamed", site.renamed(), runtime(Renamed::class)),
            agrees("standalone", site.standalone(), runtime(Standalone::class)),
            agrees("named", site.audit(), Log.named(StringBuilder("audit").toString())),
            agrees("builtin", site.builtin(), runtime(String::class)),
        ),
    )
    return "OK"
}
