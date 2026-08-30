plugins {
    id("akki.kotlin-jvm")
    `java-gradle-plugin`
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
