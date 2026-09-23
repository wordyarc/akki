package akki.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class KotlinClassesPatch(
    @get:Input val moduleName: String,
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) val classes: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("--patch-module", "$moduleName=${classes.asPath}")
}

internal val Project.jvmModuleName: String
    get() = "io.akki.${name.removePrefix("akki-").replace('-', '.')}"

internal fun Project.compileModuleDescriptor(javaCompile: TaskProvider<JavaCompile>, kotlinCompile: TaskProvider<KotlinCompile>) {
    javaCompile.configure {
        options.compilerArgumentProviders.add(
            KotlinClassesPatch(jvmModuleName, files(kotlinCompile.flatMap { it.destinationDirectory })),
        )
    }
}
