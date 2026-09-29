import akki.buildlogic.AwaitMavenCentral
import akki.buildlogic.REPOSITORY_PATH
import akki.buildlogic.REPOSITORY_URL
import akki.buildlogic.mavenGroup
import akki.buildlogic.testRepositoryCategory
import akki.buildlogic.unpublishTestFixtures
import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.GradlePublishPlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    base
    id("akki.base")
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    coordinates(groupId = mavenGroup)
    publishToMavenCentral(automaticRelease = false, validateDeployment = DeploymentValidation.VALIDATED)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    pom {
        name = project.name
        description = provider { requireNotNull(project.description) { "${project.path} has no description" } }
        url = REPOSITORY_URL
        inceptionYear = "2026"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "wordyarc"
                name = "Viktor Kogai"
                url = "https://github.com/wordyarc"
            }
        }
        scm {
            url = REPOSITORY_URL
            connection = "scm:git:$REPOSITORY_URL.git"
            developerConnection = "scm:git:ssh://git@github.com/$REPOSITORY_PATH.git"
        }
    }
}

afterEvaluate {
    val javadocJar = JavadocJar.Empty()
    val platform = when {
        pluginManager.hasPlugin("com.gradle.plugin-publish") -> GradlePublishPlugin()
        pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform") -> KotlinMultiplatform(javadocJar)
        else -> KotlinJvm(javadocJar)
    }
    mavenPublishing.configure(platform)
    if (pluginManager.hasPlugin("java-test-fixtures")) unpublishTestFixtures()
}

tasks.register<AwaitMavenCentral>("awaitMavenCentral") {
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    description = "Waits until Maven Central serves the POM of every publication"
    poms = provider {
        publishing.publications.withType<MavenPublication>().map {
            "${it.groupId.replace('.', '/')}/${it.artifactId}/${it.version}/${it.artifactId}-${it.version}.pom"
        }
    }
}

val testRepositoryDirectory = layout.buildDirectory.dir("publications/test-repository")

val testRepository = publishing.repositories.maven {
    name = "test"
    url = uri(testRepositoryDirectory)
}

val toTestRepository = "To${testRepository.name.replaceFirstChar(Char::uppercaseChar)}Repository"

val publishToTestRepository = tasks.named("publishAllPublications$toTestRepository")

val cleanTestRepository = tasks.register<Delete>("cleanTestRepository") {
    delete(testRepositoryDirectory)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith(toTestRepository)) dependsOn(cleanTestRepository)
}

configurations.consumable("testRepositoryElements") {
    attributes.attribute(Category.CATEGORY_ATTRIBUTE, testRepositoryCategory)
    outgoing.artifact(testRepositoryDirectory) {
        builtBy(publishToTestRepository)
    }
}

tasks.check {
    dependsOn(publishToTestRepository)
}
