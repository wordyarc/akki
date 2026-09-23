package consumer

import io.akki.LOGGER_NAME_STYLE_PROPERTY_NAME
import io.akki.LOGGER_NAME_STYLE_VALUE_JVM_CLASS
import io.akki.LOGGER_NAME_STYLE_VALUE_SOURCE
import io.akki.Log

class Owner {
    class Nested
}

fun main() {
    println("AKKI entered main")
    val startupStyle = System.getProperty(LOGGER_NAME_STYLE_PROPERTY_NAME)
    System.setProperty(
        LOGGER_NAME_STYLE_PROPERTY_NAME,
        if (startupStyle == LOGGER_NAME_STYLE_VALUE_SOURCE) LOGGER_NAME_STYLE_VALUE_JVM_CLASS else LOGGER_NAME_STYLE_VALUE_SOURCE,
    )
    val logger = Log.of<Owner.Nested>()
    check(logger === Log.of(Owner.Nested::class.java))
    println("AKKI name=${logger.name}")
}
