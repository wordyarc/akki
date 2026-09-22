package akki.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class WriteVersionConstant : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val directory = outputDirectory.get().asFile.resolve(packageName.get().replace('.', '/'))
        directory.mkdirs()
        directory.resolve("AkkiVersion.kt").writeText(
            "package ${packageName.get()}\n\ninternal const val AKKI_VERSION: String = \"${version.get()}\"\n",
        )
    }
}
