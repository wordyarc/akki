package io.akki.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
internal abstract class WriteAkkiProperties : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val loggerNameStyle: Property<LoggerNameStyle>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val directory = outputDirectory.get().asFile
        directory.deleteRecursively()
        directory.mkdirs()
        val style = loggerNameStyle.orNull ?: return
        val file = directory.resolve(AKKI_PROPERTIES_RESOURCE)
        file.parentFile.mkdirs()
        file.writeText("$LOGGER_NAME_STYLE_PROPERTY=${style.name.lowercase().replace('_', '-')}\n")
    }

    companion object {
        const val AKKI_PROPERTIES_RESOURCE: String = "META-INF/akki.properties"
        const val LOGGER_NAME_STYLE_PROPERTY: String = "io.akki.loggerNameStyle"

        fun name(sourceSet: String): String =
            "write${sourceSet.replaceFirstChar(Char::uppercaseChar)}AkkiProperties"
    }
}
