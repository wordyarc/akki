import akki.buildlogic.ClasspathSystemProperty
import akki.buildlogic.REPOSITORY_URL
import akki.buildlogic.compilerArtifactId
import akki.buildlogic.gradlePluginId
import akki.buildlogic.kotlinLine
import akki.buildlogic.mavenGroup
import akki.buildlogic.testRepositoriesOf

plugins {
    id("akki.kotlin-jvm")
    id("akki.publishing")
    id("com.gradle.plugin-publish")
}

description = "Gradle plugin that applies the akki compiler plugin for the project's Kotlin line, adds akki-core and " +
    "akki-slf4j of the same version and passes the configured logger name style to Test and JavaExec tasks"

val testRepositories: Configuration =
    testRepositoriesOf(":akki-core", ":akki-slf4j", ":akki-compiler", ":akki-gradle", ":akki-test")

val prepareTestRepository = tasks.register<Sync>("prepareTestRepository") {
    into(layout.buildDirectory.dir("test-repository"))
    from(testRepositories)
}

fun artifactConfiguration(name: String): Configuration = configurations.create(name) {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val akkiCoreJar: Configuration = artifactConfiguration("akkiCoreJar")
val akkiSlf4jJar: Configuration = artifactConfiguration("akkiSlf4jJar")
val akkiTestJar: Configuration = artifactConfiguration("akkiTestJar")

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)
    akkiCoreJar(project(":akki-core"))
    akkiSlf4jJar(project(":akki-slf4j"))
    akkiTestJar(project(":akki-test"))
    testImplementation(gradleTestKit())
}

kotlin {
    explicitApi()
}

val writeAkkiGradleProperties = tasks.register<WriteProperties>("writeAkkiGradleProperties") {
    destinationFile = layout.buildDirectory.file("generated/akki-gradle.properties")
    property("group", mavenGroup)
    property("version", project.version.toString())
    property("compiler.$kotlinLine", compilerArtifactId)
}

tasks.processResources {
    from(writeAkkiGradleProperties)
}

sourceSets.test {
    resources.srcDir("src/test/data")
}

tasks.test {
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.test.repository", files(prepareTestRepository)))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.core.jar", akkiCoreJar))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.slf4j.jar", akkiSlf4jJar))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.test.jar", akkiTestJar))
    systemProperty("org.gradle.testkit.dir", layout.buildDirectory.dir("test-kit").get().asFile)
    systemProperty("akki.maven.group", mavenGroup)
    systemProperty("akki.plugin.id", gradlePluginId)
    systemProperty("akki.version", project.version.toString())
    systemProperty("akki.kotlin.version", libs.versions.kotlin.get())
    systemProperty("akki.kotlin.tested", providers.gradleProperty("akki.kotlin.tested").get())
    systemProperty("akki.slf4j.version", libs.versions.slf4j.get())
    systemProperty("akki.logback.version", libs.versions.logback.get())
}

gradlePlugin {
    website = REPOSITORY_URL
    vcsUrl = REPOSITORY_URL
    plugins {
        create("akki") {
            id = gradlePluginId
            displayName = "Akki"
            description = project.description
            implementationClass = "io.akki.gradle.AkkiGradlePlugin"
            tags = listOf("kotlin", "logging", "compiler-plugin")
        }
    }
}
