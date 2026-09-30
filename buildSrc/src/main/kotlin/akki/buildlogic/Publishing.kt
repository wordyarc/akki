package akki.buildlogic

import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.kotlin.dsl.get

internal fun Project.unpublishTestFixtures() {
    val java = components["java"] as AdhocComponentWithVariants
    listOf("testFixturesApiElements", "testFixturesRuntimeElements", "testFixturesSourcesElements").forEach {
        java.withVariantsFromConfiguration(configurations[it]) { skip() }
    }
}
