plugins {
    id("akki.base")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    coordinates(groupId = providers.gradleProperty("akki.maven.group").get())
    publishToMavenCentral()
    signAllPublications()

    pom {
        name = project.name
        description = provider { requireNotNull(project.description) { "${project.path} has no description" } }
        url = "https://github.com/wordyarc/akki"
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
            url = "https://github.com/wordyarc/akki"
            connection = "scm:git:https://github.com/wordyarc/akki.git"
            developerConnection = "scm:git:ssh://git@github.com/wordyarc/akki.git"
        }
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

val cleanTestRepository = tasks.register<Delete>("cleanTestRepository") {
    delete(testRepository)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToTestRepository")) dependsOn(cleanTestRepository)
}
