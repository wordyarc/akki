package fixture

import dev.ashenarx.akki.*

fun topLevel(): String = log.name

class Service {
    fun member(): String = log.name

    fun lambdaPassedToAnInlineFunction(): String = listOf(0).map { log.name }.single()
}

inline fun explicitLoggerIsFine(): String = Log.named("explicit").name

interface Contract {
    fun defaultMethod(): String = log.name
}
