plugins {
    id("lokki.kotlin-jvm")
    alias(libs.plugins.buildconfig)
    `java-gradle-plugin`
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf("testResources"))
    }
}

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
    testImplementation(libs.kotlin.test.junit5)
}

buildConfig {
    packageName("org.jetbrains.kotlin.compiler.plugin.template")

    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${providers.gradleProperty("lokki.plugin.id").get()}\"")

    val pluginProject = project(":lokki-compiler")
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")

    val annotationsProject = project(":lokki-annotations")
    buildConfigField(
        type = "String",
        name = "ANNOTATIONS_LIBRARY_COORDINATES",
        expression = "\"${annotationsProject.group}:${annotationsProject.name}:${annotationsProject.version}\""
    )
}

gradlePlugin {
    plugins {
        create("SimplePlugin") {
            id = providers.gradleProperty("lokki.plugin.id").get()
            displayName = "Lokki"
            description = "Adds the Lokki compiler plugin to Kotlin compilations"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.template.SimpleGradlePlugin"
        }
    }
}
