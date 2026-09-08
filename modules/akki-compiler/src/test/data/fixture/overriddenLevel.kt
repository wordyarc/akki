package fixture

import io.akki.*

class Custom : Logger() {
    override val name: String = "custom"

    override fun isEnabled(level: Level): Boolean = true

    override fun emit(level: Level, message: String, cause: Throwable?, fields: Map<String, Any?>) = Unit

    override fun info(message: String, cause: Throwable?, fields: Map<String, Any?>) = Unit
}
