import akki.buildlogic.ClasspathSystemProperty
import akki.buildlogic.compilerArtifactId
import akki.buildlogic.kotlinLine

plugins {
    id("akki.kotlin-jvm")
    id("akki.publishing")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
}

description = "Gradle plugin that applies the akki compiler plugin and adds akki-core"

val prepareTestRepository = tasks.register<Sync>("prepareTestRepository") {
    into(layout.buildDirectory.dir("test-repository"))
    listOf("akki-core", "akki-slf4j", "akki-compiler", "akki-gradle", "akki-test").forEach { module ->
        dependsOn(":$module:publishAllPublicationsToTestRepository")
        from(project(":$module").layout.buildDirectory.dir("publications/test-repository"))
    }
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
    property("group", providers.gradleProperty("akki.maven.group").get())
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
    systemProperty("akki.maven.group", providers.gradleProperty("akki.maven.group").get())
    systemProperty("akki.plugin.id", providers.gradleProperty("akki.plugin.id").get())
    systemProperty("akki.version", project.version.toString())
    systemProperty("akki.kotlin.version", libs.versions.kotlin.get())
    systemProperty("akki.kotlin.tested", providers.gradleProperty("akki.kotlin.tested").get())
    systemProperty("akki.slf4j.version", libs.versions.slf4j.get())
    systemProperty("akki.logback.version", libs.versions.logback.get())
}

gradlePlugin {
    website = "https://github.com/wordyarc/akki"
    vcsUrl = "https://github.com/wordyarc/akki"
    plugins {
        create("akki") {
            id = providers.gradleProperty("akki.plugin.id").get()
            displayName = "Akki"
            description = "Adds the Akki compiler plugin to Kotlin compilations"
            implementationClass = "io.akki.gradle.AkkiGradlePlugin"
            tags = listOf("kotlin", "logging", "compiler-plugin")
        }
    }
}
