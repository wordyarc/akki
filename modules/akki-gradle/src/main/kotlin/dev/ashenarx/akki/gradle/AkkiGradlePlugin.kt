package dev.ashenarx.akki.gradle

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public interface AkkiExtension {
    public val enabled: Property<Boolean>
}

public class AkkiGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create(EXTENSION_NAME, AkkiExtension::class.java).enabled.convention(true)
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
        val extension = project.extensions.getByType(AkkiExtension::class.java)
        kotlinCompilation.defaultSourceSet.dependencies {
            implementation("$PLUGIN_GROUP:$CORE_ARTIFACT:$pluginVersion")
        }
        return extension.enabled.map { listOf(SubpluginOption("enabled", it.toString())) }
    }

    private companion object {
        const val EXTENSION_NAME: String = "akki"
        const val PLUGIN_ID: String = "dev.ashenarx.akki"
        const val PLUGIN_GROUP: String = "dev.ashenarx"
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
