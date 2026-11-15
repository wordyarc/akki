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

public class AkkiBackend private constructor(
    internal val className: String?,
    internal val module: String?,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is AkkiBackend && className == other.className && module == other.module)

    override fun hashCode(): Int = 31 * className.hashCode() + module.hashCode()

    override fun toString(): String = "AkkiBackend(${className ?: "none"})"

    public companion object {
        @JvmField
        public val SLF4J: AkkiBackend = AkkiBackend("io.akki.slf4j.Slf4jBackend", "akki-slf4j")

        @JvmField
        public val SYSTEM_LOGGER: AkkiBackend = AkkiBackend("io.akki.system.SystemLoggerBackend", "akki-system")

        @JvmField
        public val NONE: AkkiBackend = AkkiBackend(null, null)

        private val builtIn: List<AkkiBackend> = listOf(SLF4J, SYSTEM_LOGGER)

        @JvmStatic
        public fun fromClassName(className: String): AkkiBackend {
            require(className.isClassName()) {
                "akki: '$className' is not a class name: expected the class name of a LogBackend, " +
                    "for example com.example.TenantBackend"
            }
            return builtIn.firstOrNull { it.className == className } ?: AkkiBackend(className, null)
        }

        private fun String.isClassName(): Boolean = split('.').all { segment ->
            segment.isNotEmpty() && segment.first().isJavaIdentifierStart() && segment.all { it.isJavaIdentifierPart() }
        }
    }
}

public abstract class AkkiCompilerOptions {

    public abstract val minLevel: Property<MinLevel>
}

public abstract class AkkiSlf4jOptions {

    public abstract val context: Property<Slf4jContext>
}

public abstract class AkkiJvmOptions {

    public abstract val loggerNameStyle: Property<LoggerNameStyle>

    public abstract val backend: Property<AkkiBackend>

    @get:Nested
    public abstract val slf4j: AkkiSlf4jOptions

    public fun slf4j(action: Action<AkkiSlf4jOptions>) {
        action.execute(slf4j)
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
