import org.gradle.api.tasks.WriteProperties
import org.gradle.api.tasks.bundling.Jar

plugins {
    id("akki.kotlin-jvm")
    `java-gradle-plugin`
}

evaluationDependsOn(":akki-compiler")
evaluationDependsOn(":akki-core")

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val compilerPluginJar = project(":akki-compiler").tasks.named<Jar>("jar")
val coreJar = project(":akki-core").tasks.named<Jar>("jvmJar")
val gradlePluginJar = tasks.named<Jar>("jar")
val compilerPluginJarPath = compilerPluginJar.flatMap { it.archiveFile }.get().asFile.absolutePath
val coreJarPath = coreJar.flatMap { it.archiveFile }.get().asFile.absolutePath
val gradlePluginJarPath = gradlePluginJar.flatMap { it.archiveFile }.get().asFile.absolutePath

tasks.test {
    dependsOn(compilerPluginJar, coreJar, gradlePluginJar)
    systemProperty("akki.compiler.plugin.jar", compilerPluginJarPath)
    systemProperty("akki.core.jar", coreJarPath)
    systemProperty("akki.gradle.plugin.jar", gradlePluginJarPath)
}

val akkiVersion = project.version.toString()
val writeAkkiGradleProperties = tasks.register<WriteProperties>("writeAkkiGradleProperties") {
    destinationFile = layout.buildDirectory.file("generated/akki-gradle.properties")
    property("version", akkiVersion)
}

tasks.processResources {
    from(writeAkkiGradleProperties)
}

kotlin {
    explicitApi()
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
