package akki.buildlogic

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named

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

internal val Project.testRepositoryCategory: Category
    get() = objects.named("akki-test-repository")

fun Project.testRepositoryOf(vararg projectPaths: String): Configuration {
    val repository = configurations.create("testRepository") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, testRepositoryCategory)
    }
    projectPaths.forEach { dependencies.add(repository.name, dependencies.project(it)) }
    return repository
}
