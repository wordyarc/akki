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

public abstract class AkkiCompilerOptions {

    public abstract val minLevel: Property<MinLevel>
}

public abstract class AkkiJvmOptions {

    public abstract val loggerNameStyle: Property<LoggerNameStyle>
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
