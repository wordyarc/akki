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

public abstract class AkkiExtension {

    public abstract val minLevel: Property<MinLevel>
}
