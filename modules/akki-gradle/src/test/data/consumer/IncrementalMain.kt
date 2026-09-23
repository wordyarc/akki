package consumer

import io.akki.log

class Unchanged {
    fun name(): String = log.name
}

fun main() {
    val service = ChangingService()
    println("AKKI owner=${service.owner()} audit=${service.audit()} retained=${Unchanged().name()}")
}
