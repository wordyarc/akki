package io.akki.gradle

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.w3c.dom.Element

class PublishedDependenciesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("artifacts")
    fun `publishes exactly the declared dependencies`(artifact: String, expected: Set<Dependency>) {
        assertEquals(expected, dependenciesOf(artifact))
    }

    data class Dependency(val group: String, val artifact: String, val scope: String)

    private fun dependenciesOf(artifact: String): Set<Dependency> {
        val pom = path("akki.test.repository").resolve("${GROUP.replace('.', '/')}/$artifact/$VERSION")
            .listDirectoryEntries("*.pom").single()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile())
        val elements = document.getElementsByTagName("dependency")
        return (0 until elements.length).mapTo(mutableSetOf()) { index ->
            val dependency = elements.item(index) as Element
            Dependency(dependency.text("groupId"), dependency.text("artifactId"), dependency.text("scope"))
        }
    }

    private fun Element.text(tag: String): String = getElementsByTagName(tag).item(0).textContent

    private companion object {
        val STDLIB: Dependency = Dependency("org.jetbrains.kotlin", "kotlin-stdlib", "compile")
        val COROUTINES: Dependency = Dependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "compile")
        val BOM: Dependency = Dependency(GROUP, "akki-bom", "import")

        fun akki(artifact: String, scope: String = "compile"): Dependency = Dependency(GROUP, artifact, scope)

        @JvmStatic
        fun artifacts(): List<Arguments> = listOf(
            Arguments.of("akki-core-jvm", setOf(STDLIB, BOM)),
            Arguments.of("akki-slf4j", setOf(akki("akki-core-jvm"), STDLIB, BOM)),
            Arguments.of("akki-test-jvm", setOf(akki("akki-core-jvm"), STDLIB, BOM)),
            Arguments.of("akki-coroutines-jvm", setOf(akki("akki-core-jvm"), COROUTINES, STDLIB, BOM)),
            Arguments.of("akki-context-propagation", setOf(akki("akki-core-jvm"), STDLIB, BOM)),
            Arguments.of(
                "akki-test-coroutines-jvm",
                setOf(akki("akki-test-jvm"), akki("akki-coroutines-jvm", "runtime"), COROUTINES, STDLIB, BOM),
            ),
        )
    }
}
