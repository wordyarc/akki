package akki.buildlogic

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named

internal const val REPOSITORY_PATH: String = "wordyarc/akki"

const val REPOSITORY_URL: String = "https://github.com/$REPOSITORY_PATH"

val Project.mavenGroup: String
    get() = providers.gradleProperty("akki.maven.group").get()

val Project.gradlePluginId: String
    get() = providers.gradleProperty("akki.plugin.id").get()

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

internal val Project.testRepositoryCategory: Category
    get() = objects.named("akki-test-repository")

fun Project.testRepositoriesOf(vararg projectPaths: String): Configuration {
    val repositories = configurations.create("testRepositories") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, testRepositoryCategory)
    }
    projectPaths.forEach { dependencies.add(repositories.name, dependencies.project(it)) }
    return repositories
}
