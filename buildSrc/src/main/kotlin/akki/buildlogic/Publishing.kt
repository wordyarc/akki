package akki.buildlogic

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get

fun Project.publishAs(artifactId: String) {
    extensions.configure<MavenPublishBaseExtension> {
        coordinates(artifactId = artifactId)
        pom { name.set(artifactId) }
    }
}

internal fun Project.unpublishTestFixtures() {
    val java = components["java"] as AdhocComponentWithVariants
    listOf("testFixturesApiElements", "testFixturesRuntimeElements", "testFixturesSourcesElements").forEach {
        java.withVariantsFromConfiguration(configurations[it]) { skip() }
    }
}
