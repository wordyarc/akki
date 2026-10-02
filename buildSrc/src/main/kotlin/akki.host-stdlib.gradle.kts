check(!pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
    "akki: ${project.path} applies akki.host-stdlib after the Kotlin plugin, which has already added kotlin-stdlib"
}

extra["kotlin.stdlib.default.dependency"] = "false"
