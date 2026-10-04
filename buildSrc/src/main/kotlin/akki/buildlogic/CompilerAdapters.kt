package akki.buildlogic

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

const val COMPILER_COMPAT_PROJECT: String = ":akki-compiler-compat"

const val COMPILER_COMPAT_SERVICE: String = "META-INF/services/io.akki.compiler.compat.CompilerCompat\$Factory"

const val COMPILER_ADAPTERS_MANIFEST: String = "modules/akki-compiler-compat/adapters.properties"

private const val FACTORY_NAME: String = "CompilerCompatFactory"

private const val KOTLIN_COMPILER: String = "org.jetbrains.kotlin:kotlin-compiler"

private val qualifiedClassName = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")

class CompilerAdapter internal constructor(val minVersion: String, val implementationClassName: String) {
    val projectPath: String
        get() = "$COMPILER_COMPAT_PROJECT:kotlin-$minVersion"

    val packageName: String
        get() = implementationClassName.substringBeforeLast('.')

    val factoryClassName: String
        get() = factoryClassNameOf(implementationClassName)

    override fun toString(): String = "$minVersion=$implementationClassName"
}

internal fun factoryClassNameOf(implementationClassName: String): String =
    "${implementationClassName.substringBeforeLast('.')}.$FACTORY_NAME"

internal fun isQualifiedClassName(name: String): Boolean = qualifiedClassName.matches(name)

internal fun File.writeGenerated(text: String) {
    parentFile.mkdirs()
    writeText(text)
}

internal fun parseCompilerAdapters(manifest: String): List<CompilerAdapter> {
    val adapters = manifest.lineSequence()
        .map(String::trim)
        .filterNot { it.isEmpty() || it.startsWith('#') }
        .map(::parseCompilerAdapter)
        .toList()
    if (adapters.isEmpty()) invalid("no adapters are declared")
    adapters.requireUnique("version") { it.minVersion }
    adapters.requireUnique("factory") { it.factoryClassName }
    return adapters.sortedBy { kotlinVersion(it.minVersion) }
}

private fun parseCompilerAdapter(entry: String): CompilerAdapter {
    val version = entry.substringBefore('=').trim()
    val implementation = entry.substringAfter('=', "").trim()
    if (runCatching { kotlinVersion(version) }.isFailure) invalid("'$version' is not a Kotlin version")
    if (!isQualifiedClassName(implementation)) invalid("'$implementation' of $version is not a qualified class name")
    if (implementation.substringAfterLast('.') == FACTORY_NAME) {
        invalid("'$implementation' of $version takes the name of the generated factory")
    }
    return CompilerAdapter(version, implementation)
}

private fun kotlinVersion(version: String): KotlinToolingVersion = KotlinToolingVersion(version)

private fun List<CompilerAdapter>.requireUnique(property: String, selector: (CompilerAdapter) -> String) {
    val repeated = groupBy(selector).entries.firstOrNull { it.value.size > 1 } ?: return
    invalid("$property ${repeated.key} is declared ${repeated.value.size} times")
}

private fun invalid(problem: String): Nothing = throw GradleException("$COMPILER_ADAPTERS_MANIFEST: $problem")

val Project.compilerAdapters: List<CompilerAdapter>
    get() {
        val manifest = isolated.rootProject.projectDirectory.file(COMPILER_ADAPTERS_MANIFEST)
        return parseCompilerAdapters(providers.fileContents(manifest).asText.get())
    }

val Project.compilerAdapter: CompilerAdapter
    get() = compilerAdapters.firstOrNull { it.projectPath == path } ?: invalid("project $path has no adapter")

val Project.compilerApiBaseline: String
    get() = kotlinVersion(compilerAdapters.first().minVersion).run { "$major.$minor.$patch" }

val Project.testedKotlin: List<String>
    get() = kotlinVersions("akki.kotlin.tested")

val Project.rejectedKotlin: List<String>
    get() = kotlinVersions("akki.kotlin.rejected")

val Project.latestTestedKotlin: String
    get() = testedKotlin.maxBy(::kotlinVersion)

private fun Project.kotlinVersions(property: String): List<String> =
    providers.gradleProperty(property).get().split(',')

fun Project.compileAgainstCompilerApi(compilerVersion: String) {
    dependencies.add("compileOnly", KOTLIN_COMPILER) {
        version { strictly(compilerVersion) }
    }
    val oldest = kotlinVersion(compilerApiBaseline)
    tasks.named<KotlinCompile>("compileKotlin") {
        compilerOptions.apiVersion.set(KotlinVersion.fromVersion("${oldest.major}.${oldest.minor}"))
    }
}

fun Project.checkLatestCompilerApi(): SourceSet {
    val latest = latestTestedKotlin
    val sourceSet = extensions.getByType<SourceSetContainer>().create("latestCompilerApi")
    val kotlin = extensions.getByType<KotlinJvmProjectExtension>()
    kotlin.sourceSets.named(sourceSet.name) {
        this.kotlin.source(kotlin.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).kotlin)
    }
    dependencies.add(sourceSet.compileOnlyConfigurationName, KOTLIN_COMPILER) {
        version { strictly(latest) }
    }
    val compile = tasks.named<KotlinCompile>(sourceSet.getCompileTaskName("kotlin")) {
        description = "Compiles the main sources against the compiler API of Kotlin $latest, the latest tested " +
            "release, without failing on its warnings"
        compilerOptions.allWarningsAsErrors.set(false)
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(compile) }
    return sourceSet
}
