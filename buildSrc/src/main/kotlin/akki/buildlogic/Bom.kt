package akki.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

val BOM_ARTIFACTS: List<String> = listOf(
    "akki-core",
    "akki-core-jvm",
    "akki-slf4j",
    "akki-test",
    "akki-test-jvm",
    "akki-coroutines",
    "akki-coroutines-jvm",
    "akki-test-coroutines",
    "akki-test-coroutines-jvm",
    "akki-compiler",
    "akki-gradle",
)

abstract class CheckBomArtifacts : DefaultTask() {
    @get:Input
    abstract val published: ListProperty<String>

    @TaskAction
    fun check() {
        val unmanaged = published.get() - BOM_ARTIFACTS.toSet()
        if (unmanaged.isNotEmpty()) {
            throw GradleException("akki-bom does not manage ${unmanaged.joinToString()}: add to BOM_ARTIFACTS")
        }
    }
}

internal fun Project.checkBomArtifacts() {
    val publications = extensions.getByType<PublishingExtension>().publications.withType<MavenPublication>()
    val managedGroup = mavenGroup
    val check = tasks.register<CheckBomArtifacts>("checkBomArtifacts") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks that akki-bom manages every artifact this module publishes"
        published.set(provider { publications.filter { it.groupId == managedGroup }.map { it.artifactId } })
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(check) }
}
