package dev.ashenarx.akki.gradle

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

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> = kotlinCompilation.target.project.provider { emptyList() }

    private companion object {
        const val PLUGIN_ID: String = "dev.ashenarx.akki"
        const val PLUGIN_GROUP: String = "dev.ashenarx"
        const val COMPILER_ARTIFACT: String = "akki-compiler"

        val pluginVersion: String by lazy {
            val properties = Properties()
            val resource = requireNotNull(
                AkkiGradlePlugin::class.java.getResourceAsStream("/akki-gradle.properties")
            )
            resource.use(properties::load)
            requireNotNull(properties.getProperty("version"))
        }
    }
}
