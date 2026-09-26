package io.akki.gradle

import org.gradle.api.provider.Property

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

public abstract class AkkiExtension {

    public abstract val minLevel: Property<MinLevel>

    public abstract val loggerNameStyle: Property<LoggerNameStyle>
}
