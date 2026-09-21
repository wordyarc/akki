import akki.buildlogic.ClasspathSystemProperty

plugins {
    id("akki.kotlin-jvm")
    `java-gradle-plugin`
}

fun artifactConfiguration(name: String): Configuration = configurations.create(name) {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val akkiCompilerJar: Configuration = artifactConfiguration("akkiCompilerJar")
val akkiCoreJar: Configuration = artifactConfiguration("akkiCoreJar")
val akkiSlf4jJar: Configuration = artifactConfiguration("akkiSlf4jJar")

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)
    akkiCompilerJar(project(":akki-compiler"))
    akkiCoreJar(project(":akki-core"))
    akkiSlf4jJar(project(":akki-slf4j"))
    testImplementation(gradleTestKit())
}

kotlin {
    explicitApi()
}

val writeAkkiGradleProperties = tasks.register<WriteProperties>("writeAkkiGradleProperties") {
    destinationFile = layout.buildDirectory.file("generated/akki-gradle.properties")
    property("version", project.version.toString())
    property("kotlin", libs.versions.kotlin.get())
}

tasks.processResources {
    from(writeAkkiGradleProperties)
}

sourceSets.test {
    resources.srcDir("src/test/data")
}

tasks.test {
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.compiler.plugin.jar", akkiCompilerJar))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.core.jar", akkiCoreJar))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.slf4j.jar", akkiSlf4jJar))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.gradle.plugin.jar", files(tasks.jar)))
    systemProperty("akki.version", project.version.toString())
    systemProperty("akki.kotlin.version", libs.versions.kotlin.get())
    systemProperty("akki.slf4j.version", libs.versions.slf4j.get())
    systemProperty("akki.logback.version", libs.versions.logback.get())
}

gradlePlugin {
    plugins {
        create("akki") {
            id = providers.gradleProperty("akki.plugin.id").get()
            displayName = "Akki"
            description = "Adds the Akki compiler plugin to Kotlin compilations"
            implementationClass = "io.akki.gradle.AkkiGradlePlugin"
        }
    }
}
