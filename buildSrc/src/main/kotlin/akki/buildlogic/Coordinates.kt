package akki.buildlogic

import org.gradle.api.Project

internal const val REPOSITORY_PATH: String = "wordyarc/akki"

const val REPOSITORY_URL: String = "https://github.com/$REPOSITORY_PATH"

internal val Project.identity: String
    get() = providers.gradleProperty("akki.group").get()

val Project.mavenGroup: String
    get() = providers.gradleProperty("akki.maven.group").get()

val Project.gradlePluginId: String
    get() = providers.gradleProperty("akki.plugin.id").get()
