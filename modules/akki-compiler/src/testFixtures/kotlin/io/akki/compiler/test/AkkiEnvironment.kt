@file:OptIn(ExperimentalCompilerApi::class)

package io.akki.compiler.test

import io.akki.compiler.AkkiCompilerPluginRegistrar
import io.akki.compiler.MIN_LEVEL
import java.io.File
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.AnalysisFlag
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.WarningLevel
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.DirectiveToConfigurationKeyExtractor
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices

internal fun TestConfigurationBuilder.configureAkki() {
    useDirectives(AkkiDirectives)
    useConfigurators(::AkkiEnvironmentConfigurator)
    useCustomRuntimeClasspathProviders(::AkkiRuntimeClasspathProvider)
    defaultDirectives {
        +ConfigurationDirectives.WITH_STDLIB
        +JvmEnvironmentConfigurationDirectives.FULL_JDK
        +CodegenTestDirectives.IGNORE_DEXING
        JvmEnvironmentConfigurationDirectives.JVM_TARGET with jvmTarget
    }
}

private val fixtureClasspath: List<File> = property("akki.fixture.classpath").split(File.pathSeparator).map(::File)

private val jvmTarget: JvmTarget =
    requireNotNull(JvmTarget.fromString(property("akki.jvm.target"))) { "unknown JVM target in -Dakki.jvm.target" }

private fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }

private class AkkiEnvironmentConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(AkkiDirectives)

    override fun DirectiveToConfigurationKeyExtractor.provideConfigurationKeys() {
        register(AkkiDirectives.MIN_LEVEL, MIN_LEVEL)
    }

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        if (AkkiDirectives.WITHOUT_AKKI !in module.directives) configuration.addJvmClasspathRoots(fixtureClasspath)
    }

    override fun provideAdditionalAnalysisFlags(
        directives: RegisteredDirectives,
        languageVersion: LanguageVersion,
    ): Map<AnalysisFlag<*>, Any?> {
        val levels = directives[AkkiDirectives.WARNING_LEVEL].associate { override ->
            val level = requireNotNull(WarningLevel.fromString(override.substringAfter(':'))) {
                "unknown warning level in '$override'"
            }
            override.substringBefore(':') to level
        }
        return if (levels.isEmpty()) emptyMap() else mapOf(AnalysisFlags.warningLevels to levels)
    }

    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        if (AkkiDirectives.WITHOUT_PLUGIN in module.directives) return
        with(AkkiCompilerPluginRegistrar()) { registerExtensions(configuration) }
    }
}

private class AkkiRuntimeClasspathProvider(testServices: TestServices) : RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> =
        if (AkkiDirectives.WITHOUT_AKKI in module.directives) emptyList() else fixtureClasspath
}
