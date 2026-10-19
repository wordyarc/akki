package io.akki.gradle

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

public enum class MinLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF,
}

public enum class LoggerNameStyle {
    SOURCE,
    JVM_CLASS,
}

public enum class Slf4jContext {
    KEY_VALUE_PAIRS,
    MDC,
}

public abstract class AkkiCompilerOptions {

    public abstract val minLevel: Property<MinLevel>
}

public abstract class AkkiJvmOptions {

    public abstract val loggerNameStyle: Property<LoggerNameStyle>

    public abstract val backend: Property<String>

    public abstract val includeSlf4jBackend: Property<Boolean>

    public abstract val slf4jContext: Property<Slf4jContext>

    init {
        includeSlf4jBackend.convention(true)
    }

    public companion object {
        public const val SLF4J_BACKEND: String = "io.akki.slf4j.Slf4jBackend"
    }
}

public abstract class AkkiExtension {

    @get:Nested
    public abstract val compilerOptions: AkkiCompilerOptions

    @get:Nested
    public abstract val jvm: AkkiJvmOptions

    public fun compilerOptions(action: Action<AkkiCompilerOptions>) {
        action.execute(compilerOptions)
    }

    public fun jvm(action: Action<AkkiJvmOptions>) {
        action.execute(jvm)
    }
}
