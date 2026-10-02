package io.akki.gradle

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class AkkiGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, AkkiExtension::class.java)
        val versions = target.configurations.create(VERSIONS_CONFIGURATION_NAME) {
            it.description =
                "Pins $core and $slf4j to the compiler plugin version in compilations using the plugin"
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
        }
        listOf(core, "$core-jvm", slf4j).forEach { module ->
            target.dependencies.constraints.add(versions.name, module) { constraint ->
                constraint.version { it.strictly(pluginVersion) }
                constraint.because("Akki modules require the same version as the compiler plugin")
            }
        }
        val style = extension.jvm.loggerNameStyle
        target.tasks.withType(Test::class.java).configureEach { it.passLoggerNameStyle(target.providers, style) }
        target.tasks.withType(JavaExec::class.java).configureEach { it.passLoggerNameStyle(target.providers, style) }
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
        kotlinCompilation.defaultSourceSet.dependencies {
            implementation("$core:$pluginVersion")
            runtimeOnly("$slf4j:$pluginVersion")
        }
        val versions = project.configurations.getByName(VERSIONS_CONFIGURATION_NAME)
        listOfNotNull(kotlinCompilation.compileDependencyConfigurationName, kotlinCompilation.runtimeDependencyConfigurationName)
            .forEach { name -> project.configurations.named(name) { it.extendsFrom(versions) } }
        val minLevel = project.extensions.getByType(AkkiExtension::class.java).compilerOptions.minLevel
        val notice = minLevel.map { if (it == MinLevel.TRACE) "" else notice(it) }.orElse("")
        kotlinCompilation.compileTaskProvider.configure { task ->
            task.doFirst { running ->
                val message = notice.get()
                if (message.isNotEmpty()) running.logger.lifecycle(message)
            }
        }
        return minLevel.map { listOf(SubpluginOption(MIN_LEVEL_OPTION, it.name.lowercase())) }.orElse(emptyList())
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
            ) { "akki-gradle.properties is missing from the Akki Gradle plugin jar" }
            Properties().apply { resource.use(::load) }
        }

        val group: String by lazy { property("group") }

        val core: String by lazy { "$group:akki-core" }

        val slf4j: String by lazy { "$group:akki-slf4j" }

        val pluginVersion: String by lazy { property("version") }

        fun property(name: String): String =
            requireNotNull(properties.getProperty(name)) { "akki-gradle.properties has no $name" }
    }
}
