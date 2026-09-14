package fixture

import io.akki.*

@LogName("  ")
class Blank {
    fun handle(): String = log.name
}

@LogName("audit")
class Named {
    fun handle(): String = log.name
}

fun box(): String = Blank().handle() + "|" + Named().handle()
