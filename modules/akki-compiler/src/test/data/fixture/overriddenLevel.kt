package fixture

import io.akki.*
import io.akki.backend.*

class Custom : Logger() {
    override val name: String = "custom"

    override fun sink(level: Level): Sink? = null

    override fun info(message: String, cause: Throwable?, fields: Map<String, Any?>) = Unit
}
