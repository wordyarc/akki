package akki.buildlogic

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

const val COMPILER_COMPAT_PROJECT: String = ":akki-compiler-compat"

const val COMPILER_COMPAT_SERVICE: String = "META-INF/services/io.akki.compiler.compat.CompilerCompat\$Factory"

const val COMPILER_ADAPTERS_MANIFEST: String = "modules/akki-compiler-compat/adapters.properties"

private const val FACTORY_NAME: String = "CompilerCompatFactory"

private val canonicalVersion = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

private val qualifiedClassName = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")

class CompilerAdapter internal constructor(
    val minVersion: String,
    val implementationClassName: String,
    internal val components: List<Int>,
) {
    val projectPath: String
        get() = "$COMPILER_COMPAT_PROJECT:kotlin-$minVersion"

    val packageName: String
        get() = implementationClassName.substringBeforeLast('.')

    val factoryClassName: String
        get() = factoryClassNameOf(implementationClassName)
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
    return adapters.sortedWith(compareBy({ it.components[0] }, { it.components[1] }, { it.components[2] }))
}

private fun parseCompilerAdapter(entry: String): CompilerAdapter {
    val version = entry.substringBefore('=').trim()
    val implementation = entry.substringAfter('=', "").trim()
    val components = canonicalVersion.matchEntire(version)?.groupValues?.drop(1)?.mapNotNull(String::toIntOrNull)
    if (components?.size != 3) invalid("'$version' is not a MAJOR.MINOR.PATCH compiler version")
    if (!isQualifiedClassName(implementation)) invalid("'$implementation' of $version is not a qualified class name")
    if (implementation.substringAfterLast('.') == FACTORY_NAME) {
        invalid("'$implementation' of $version takes the name of the generated factory")
    }
    return CompilerAdapter(version, implementation, components)
}

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
    get() = compilerAdapters.first().minVersion

fun Project.compileAgainstCompilerApi(compilerVersion: String) {
    dependencies.add("compileOnly", "org.jetbrains.kotlin:kotlin-compiler") {
        version { strictly(compilerVersion) }
    }
    val oldestHost = KotlinVersion.fromVersion(compilerApiBaseline.substringBeforeLast('.'))
    tasks.named<KotlinCompile>("compileKotlin") {
        compilerOptions.apiVersion.set(oldestHost)
    }
}
