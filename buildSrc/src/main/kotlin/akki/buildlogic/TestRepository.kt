package akki.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

private val Project.testRepositoryCategory: Category
    get() = objects.named("akki-test-repository")

internal fun Project.publishTestRepository() {
    val directory = layout.buildDirectory.dir("publications/test-repository")
    val repository = extensions.getByType<PublishingExtension>().repositories.maven {
        name = "test"
        url = uri(directory)
    }
    val toRepository = "To${repository.name.replaceFirstChar(Char::uppercaseChar)}Repository"
    val publish = tasks.named("publishAllPublications$toRepository")
    val clean = tasks.register<Delete>("cleanTestRepository") {
        delete(directory)
    }
    tasks.withType<PublishToMavenRepository>().configureEach {
        if (name.endsWith(toRepository)) dependsOn(clean)
    }
    configurations.consumable("testRepositoryElements") {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, testRepositoryCategory)
        outgoing.artifact(directory) {
            builtBy(publish)
        }
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
        dependsOn(publish)
    }
}

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
