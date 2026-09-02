package dev.ashenarx.akki.compiler

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class LoggerAliasLoweringTest {
    @Test
    fun keepsTheLoggerNamesOfTheRemovedAliases(@TempDir directory: Path) {
        val lowered = FixtureCompiler.box(directory.resolve("lowered"), ALIAS_FIXTURE, plugin = true)
        val plain = FixtureCompiler.box(directory.resolve("plain"), ALIAS_FIXTURE, plugin = false)

        assertEquals(
            "fixture.Service,fixture.Service,fixture.Service,fixture.Holder,fixture.Fixture",
            plain.substringBefore('|'),
        )
        assertEquals(plain.substringBefore('|'), lowered.substringBefore('|'))
    }

    @Test
    fun replacesThePrivateAliasWithTheStaticField(@TempDir directory: Path) {
        val lowered = FixtureCompiler.box(directory.resolve("lowered"), ALIAS_FIXTURE, plugin = true)
        val plain = FixtureCompiler.box(directory.resolve("plain"), ALIAS_FIXTURE, plugin = false)

        assertEquals("journal,INSTANCE+journal", plain.substringAfter('|'))
        assertEquals("\$\$log,\$\$log+INSTANCE", lowered.substringAfter('|'))
    }

    @Test
    fun keepsTheEffectsOfAReceiverItNoLongerNeeds(@TempDir directory: Path) {
        val lowered = FixtureCompiler.box(directory.resolve("lowered"), RECEIVER_FIXTURE, plugin = true)
        val plain = FixtureCompiler.box(directory.resolve("plain"), RECEIVER_FIXTURE, plugin = false)

        assertEquals("receiver,receiver|fixture.Service,fixture.Service", plain)
        assertEquals(plain, lowered)
    }

    @Test
    fun keepsAliasesThatAreVisibleOutsideTheirDeclaration(@TempDir directory: Path) {
        val lowered = FixtureCompiler.box(directory.resolve("lowered"), VISIBLE_FIXTURE, plugin = true)
        val plain = FixtureCompiler.box(directory.resolve("plain"), VISIBLE_FIXTURE, plugin = false)

        assertEquals("journal+shared|fixture.Base,fixture.Service", plain)
        assertEquals(plain, lowered)
    }

    @Test
    fun keepsAliasesThatAreReferencedOrReassigned(@TempDir directory: Path) {
        val lowered = FixtureCompiler.box(directory.resolve("lowered"), IRREMOVABLE_FIXTURE, plugin = true)
        val plain = FixtureCompiler.box(directory.resolve("plain"), IRREMOVABLE_FIXTURE, plugin = false)

        assertEquals("reassigned+referenced|fixture.Service,fixture.Service", plain)
        assertEquals(plain, lowered)
    }

    private companion object {
        const val DECLARED_FIELDS: String =
            """
            private fun fields(vararg types: Class<*>): String = types
                .flatMap { type -> type.declaredFields.map { it.name } }
                .filter { !it.startsWith("${'$'}") }
                .sorted()
                .joinToString("+")
            """

        val ALIAS_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            class Service {
                private val journal = logger()

                fun probe(): String = journal.name

                fun probeOther(other: Service): String = other.journal.name

                inner class Inner {
                    fun probe(): String = journal.name
                }
            }

            object Holder {
                private val journal = Log.forCaller()

                fun probe(): String = journal.name
            }

            private val topLevel = logger()

            private fun fields(type: Class<*>): String =
                type.declaredFields.map { it.name }.sorted().joinToString("+")

            fun box(): String = listOf(
                listOf(
                    Service().probe(),
                    Service().probeOther(Service()),
                    Service().Inner().probe(),
                    Holder.probe(),
                    topLevel.name,
                ).joinToString(","),
                listOf(fields(Service::class.java), fields(Holder::class.java)).joinToString(","),
            ).joinToString("|")
            """.trimIndent()

        val RECEIVER_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            private val effects = mutableListOf<String>()

            class Service {
                private val journal = logger()

                private fun record(): Service {
                    effects += "receiver"
                    return this
                }

                fun probe(): String = record().journal.name
            }

            fun box(): String {
                val names = listOf(Service().probe(), Service().probe())
                return effects.joinToString(",") + "|" + names.joinToString(",")
            }
            """.trimIndent()

        val VISIBLE_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            abstract class Base {
                protected val journal = logger()

                fun probeBase(): String = journal.name
            }

            class Service : Base() {
                internal val shared = logger()

                fun probe(): String = shared.name
            }
            $DECLARED_FIELDS
            fun box(): String = listOf(
                fields(Base::class.java, Service::class.java),
                listOf(Service().probeBase(), Service().probe()).joinToString(","),
            ).joinToString("|")
            """.trimIndent()

        val IRREMOVABLE_FIXTURE: String =
            """
            package fixture

            import dev.ashenarx.akki.*

            class Service {
                private var reassigned = logger()
                private val referenced = logger()

                fun probeReferenced(): String = ::referenced.get().name

                fun probeReassigned(): String {
                    reassigned = Log.named(reassigned.name)
                    return reassigned.name
                }
            }
            $DECLARED_FIELDS
            fun box(): String = listOf(
                fields(Service::class.java),
                listOf(Service().probeReferenced(), Service().probeReassigned()).joinToString(","),
            ).joinToString("|")
            """.trimIndent()
    }
}
