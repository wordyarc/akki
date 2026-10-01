import akki.buildlogic.AwaitMavenCentral
import akki.buildlogic.REPOSITORY_PATH
import akki.buildlogic.REPOSITORY_URL
import akki.buildlogic.checkBomArtifacts
import akki.buildlogic.mavenGroup
import akki.buildlogic.publishTestRepository
import akki.buildlogic.unpublishTestFixtures
import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.GradlePublishPlugin
import com.vanniktech.maven.publish.JavaPlatform
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    base
    id("akki.versioning")
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
        pluginManager.hasPlugin("java-platform") -> JavaPlatform()
        pluginManager.hasPlugin("com.gradle.plugin-publish") -> GradlePublishPlugin()
        pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform") -> KotlinMultiplatform(javadocJar)
        else -> KotlinJvm(javadocJar)
    }
    mavenPublishing.configure(platform)
    if (pluginManager.hasPlugin("java-test-fixtures")) unpublishTestFixtures()
    if (platform !is JavaPlatform) checkBomArtifacts()
}

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    extensions.configure<KotlinJvmProjectExtension> {
        @OptIn(ExperimentalAbiValidation::class)
        abiValidation()
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension> {
        @OptIn(ExperimentalAbiValidation::class)
        abiValidation()
    }
}

tasks.register("releaseToMavenCentral") {
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    description = "Uploads every publication to Maven Central and releases the deployment"
    dependsOn("publishAndReleaseToMavenCentral")
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

publishTestRepository()
