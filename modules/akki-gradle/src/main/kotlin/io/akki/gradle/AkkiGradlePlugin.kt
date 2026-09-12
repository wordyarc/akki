package io.akki.gradle

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class AkkiGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create(EXTENSION, AkkiExtension::class.java)
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = PLUGIN_GROUP,
        artifactId = COMPILER_ARTIFACT,
        version = pluginVersion,
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        kotlinCompilation.defaultSourceSet.dependencies {
            implementation("$PLUGIN_GROUP:$CORE_ARTIFACT:$pluginVersion") {
                version { it.strictly(pluginVersion) }
                because("Akki core and compiler plugin versions must match")
            }
        }
        val minLevel = project.extensions.getByType(AkkiExtension::class.java).minLevel
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
            "akki: minLevel=${level.name.lowercase()}, records below it are removed from the bytecode and no " +
                "logging configuration can bring them back"

        const val EXTENSION: String = "akki"
        const val MIN_LEVEL_OPTION: String = "minLevel"
        const val PLUGIN_ID: String = "io.akki"
        const val PLUGIN_GROUP: String = "io.akki"
        const val COMPILER_ARTIFACT: String = "akki-compiler"
        const val CORE_ARTIFACT: String = "akki-core"

        val pluginVersion: String by lazy {
            val properties = Properties()
            val resource = requireNotNull(
                AkkiGradlePlugin::class.java.getResourceAsStream("/akki-gradle.properties")
            ) { "akki-gradle.properties is missing from the Akki Gradle plugin jar" }
            resource.use(properties::load)
            requireNotNull(properties.getProperty("version")) { "akki-gradle.properties has no version" }
        }
    }
}
