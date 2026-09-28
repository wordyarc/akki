import akki.buildlogic.mavenGroup
import akki.buildlogic.testRepositoryCategory
import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.JavadocJar
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

plugins {
    id("akki.base")
    id("com.vanniktech.maven.publish.base")
}

val repositoryPath = "wordyarc/akki"
val repositoryUrl = "https://github.com/$repositoryPath"

mavenPublishing {
    coordinates(groupId = mavenGroup)
    publishToMavenCentral(automaticRelease = false, validateDeployment = DeploymentValidation.PUBLISHED)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    pom {
        name = project.name
        description = provider { requireNotNull(project.description) { "${project.path} has no description" } }
        url = repositoryUrl
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
            url = repositoryUrl
            connection = "scm:git:$repositoryUrl.git"
            developerConnection = "scm:git:ssh://git@github.com/$repositoryPath.git"
        }
    }
}

afterEvaluate {
    mavenPublishing.configureBasedOnAppliedPlugins(javadocJar = JavadocJar.Empty())
    if (pluginManager.hasPlugin("java-test-fixtures")) {
        val java = components["java"] as AdhocComponentWithVariants
        val fixtures = listOf("testFixturesApiElements", "testFixturesRuntimeElements", "testFixturesSourcesElements")
        fixtures.forEach { java.withVariantsFromConfiguration(configurations[it]) { skip() } }
    }
}

pluginManager.withPlugin("com.gradle.plugin-publish") {
    extensions.configure<GradlePluginDevelopmentExtension> {
        website = repositoryUrl
        vcsUrl = repositoryUrl
    }
}

val testRepository = layout.buildDirectory.dir("publications/test-repository")

publishing {
    repositories {
        maven {
            name = "test"
            url = uri(testRepository)
        }
    }
}

configurations.consumable("testRepositoryElements") {
    attributes.attribute(Category.CATEGORY_ATTRIBUTE, testRepositoryCategory)
    outgoing.artifact(testRepository) {
        builtBy("publishAllPublicationsToTestRepository")
    }
}

val cleanTestRepository = tasks.register<Delete>("cleanTestRepository") {
    delete(testRepository)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToTestRepository")) dependsOn(cleanTestRepository)
}
