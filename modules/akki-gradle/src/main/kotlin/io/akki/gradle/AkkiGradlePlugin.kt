package io.akki.gradle

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class AkkiGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, AkkiExtension::class.java)
        val versions = target.configurations.create(VERSIONS_CONFIGURATION_NAME) {
            it.description = "Pins every akki module to the compiler plugin version in compilations using the plugin"
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
        }
        artifacts.forEach { artifact ->
            target.dependencies.constraints.add(versions.name, "$group:$artifact") { constraint ->
                constraint.version { it.strictly(pluginVersion) }
                constraint.because("akki modules must match the compiler plugin version")
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.platformType == KotlinPlatformType.jvm ||
            kotlinCompilation.platformType == KotlinPlatformType.androidJvm

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = group,
        artifactId = COMPILER_ARTIFACT,
        version = pluginVersion,
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(AkkiExtension::class.java)
        kotlinCompilation.defaultSourceSet.dependencies { implementation("$core:$pluginVersion") }
        if (kotlinCompilation.platformType == KotlinPlatformType.jvm) {
            val backend = extension.jvm.includeSlf4jBackend.map { included ->
                if (included) listOf(project.dependencies.create("$slf4j:$pluginVersion")) else emptyList()
            }
            project.configurations.named(kotlinCompilation.defaultSourceSet.runtimeOnlyConfigurationName) {
                it.dependencies.addAllLater(backend)
            }
            if (kotlinCompilation.name == KotlinCompilation.MAIN_COMPILATION_NAME) {
                kotlinCompilation.shipLoggerNameStyle(extension.jvm.loggerNameStyle)
            }
        }
        val versions = project.configurations.getByName(VERSIONS_CONFIGURATION_NAME)
        listOfNotNull(kotlinCompilation.compileDependencyConfigurationName, kotlinCompilation.runtimeDependencyConfigurationName)
            .forEach { name -> project.configurations.named(name) { it.extendsFrom(versions) } }
        val minLevel = extension.compilerOptions.minLevel
        val notice = minLevel.map { if (it == MinLevel.TRACE) "" else notice(it) }.orElse("")
        kotlinCompilation.compileTaskProvider.configure { task ->
            task.doFirst { running ->
                val message = notice.get()
                if (message.isNotEmpty()) running.logger.lifecycle(message)
            }
        }
        return minLevel.map { listOf(SubpluginOption(MIN_LEVEL_OPTION, it.name.lowercase())) }.orElse(emptyList())
    }

    private fun KotlinCompilation<*>.shipLoggerNameStyle(style: Provider<LoggerNameStyle>) {
        val project = target.project
        val sourceSet = defaultSourceSet
        val name = WriteAkkiProperties.name(sourceSet.name)
        val properties = project.tasks.register(name, WriteAkkiProperties::class.java) {
            it.loggerNameStyle.set(style)
            it.outputDirectory.set(project.layout.buildDirectory.dir("generated/akki/${sourceSet.name}/resources"))
        }
        sourceSet.resources.srcDir(properties)
    }

    private companion object {
        fun notice(level: MinLevel): String =
            "akki: minLevel=${level.name.lowercase()}, lower-level records are removed from the bytecode; " +
                "runtime logging configuration cannot restore them"

        const val EXTENSION_NAME: String = "akki"
        const val MIN_LEVEL_OPTION: String = "minLevel"
        const val COMPILER_PLUGIN_ID: String = "io.akki"
        const val COMPILER_ARTIFACT: String = "akki-compiler"
        const val VERSIONS_CONFIGURATION_NAME: String = "akkiVersions"

        val properties: Properties by lazy {
            val resource = requireNotNull(
                AkkiGradlePlugin::class.java.getResourceAsStream("/akki-gradle.properties")
            ) { "akki-gradle.properties is missing from the akki Gradle plugin jar" }
            Properties().apply { resource.use(::load) }
        }

        val group: String by lazy { property("group") }

        val core: String by lazy { "$group:akki-core" }

        val slf4j: String by lazy { "$group:akki-slf4j" }

        val artifacts: List<String> by lazy { property("artifacts").split(',') }

        val pluginVersion: String by lazy { property("version") }

        fun property(name: String): String =
            requireNotNull(properties.getProperty(name)) { "akki-gradle.properties does not define $name" }
    }
}
