package akki.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

abstract class WriteVersionConstant : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val constants: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val directory = outputDirectory.get().asFile.resolve(packageName.get().replace('.', '/'))
        directory.mkdirs()
        val declarations = constants.get().toSortedMap().entries.joinToString("") { (name, value) ->
            "\ninternal const val $name: String = \"$value\"\n"
        }
        directory.resolve("AkkiVersion.kt").writeText("package ${packageName.get()}\n$declarations")
    }
}

fun Project.generateVersionConstant(
    packageName: String,
    sourceSet: String,
    constants: Map<String, String> = emptyMap(),
) {
    val writeVersionConstant = tasks.register<WriteVersionConstant>("writeVersionConstant") {
        this.packageName.set(packageName)
        this.constants.put("AKKI_VERSION", project.version.toString())
        this.constants.putAll(constants)
        outputDirectory.set(layout.buildDirectory.dir("generated/source/version"))
    }
    extensions.getByType<KotlinProjectExtension>().sourceSets.named(sourceSet) {
        kotlin.srcDir(writeVersionConstant)
    }
}
