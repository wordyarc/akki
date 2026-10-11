package consumer

import io.akki.Log

private const val LOGGER_NAME_STYLE: String = "io.akki.loggerNameStyle"

class Owner {
    class Nested
}

fun main() {
    println("AKKI entered main")
    val startupStyle = System.getProperty(LOGGER_NAME_STYLE)
    println("AKKI startup style=$startupStyle")
    System.setProperty(LOGGER_NAME_STYLE, if (startupStyle == "source") "jvm-class" else "source")
    val logger = Log.of<Owner.Nested>()
    check(logger === Log.of(Owner.Nested::class.java))
    println("AKKI name=${logger.name}")
}
