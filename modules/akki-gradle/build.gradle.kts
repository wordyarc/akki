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

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
    akkiCompilerJar(project(":akki-compiler"))
    akkiCoreJar(project(":akki-core"))
    testImplementation(gradleTestKit())
}

kotlin {
    explicitApi()
}

val writeAkkiGradleProperties = tasks.register<WriteProperties>("writeAkkiGradleProperties") {
    destinationFile = layout.buildDirectory.file("generated/akki-gradle.properties")
    property("version", project.version.toString())
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
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.gradle.plugin.jar", files(tasks.jar)))
    systemProperty("akki.version", project.version.toString())
    systemProperty("akki.kotlin.version", libs.versions.kotlin.get())
}

gradlePlugin {
    plugins {
        create("akki") {
            id = providers.gradleProperty("akki.plugin.id").get()
            displayName = "Akki"
            description = "Adds the Akki compiler plugin to Kotlin compilations"
            implementationClass = "dev.ashenarx.akki.gradle.AkkiGradlePlugin"
        }
    }
}
