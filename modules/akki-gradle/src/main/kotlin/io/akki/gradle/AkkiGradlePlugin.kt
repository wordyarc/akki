package io.akki.gradle

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class AkkiGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project): Unit = Unit

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
        return project.provider { emptyList() }
    }

    private companion object {
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
