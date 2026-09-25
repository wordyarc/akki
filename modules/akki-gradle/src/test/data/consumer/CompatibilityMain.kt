package consumer

import io.akki.*
import io.akki.backend.*

const val AUDIT = "audit"

class Service {
    private val audit = Log.named(AUDIT)
    private val explicit = logger()

    fun handle(id: String) {
        log.debug { "debug $id" }
        log.info("handled $id")
        audit.info("audited $id")
        explicit.info("explicit $id")
        Log.of<Service>().warn { "folded $id" }
    }

    companion object {
        fun create(): Service = Service().also { log.info("created") }
    }
}

inline fun traced(id: String) {
    log.info("traced $id")
}

@OptIn(DelicateAkkiApi::class)
fun main() {
    val records = mutableListOf<String>()
    val backend = LogBackend { name -> LoggerBinding { level -> Sink { message, _, _ -> records += "$level $name $message" } } }
    Log.install(backend).use {
        Service.create().handle("A-1")
        traced("A-2")
        log.info("top-level")
    }
    println("AKKI records=${records.joinToString(", ")}")
}
