package consumer

import io.akki.DelicateAkkiApi
import io.akki.Log
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import library.ChangingService
import library.Unchanged

@OptIn(DelicateAkkiApi::class)
fun main() {
    val records = mutableListOf<String>()
    val backend = LogBackend { name ->
        LoggerBinding { level -> Sink { message, _, _ -> records += "$level $name $message" } }
    }
    val value = Log.install(backend).use { Caller().call() }
    val handler = Caller().anonymous()
    check(handler.logger === Log.of<Caller>())
    println("AKKI inline=$value anonymous=$handler:${handler.logger.name}")
    println("AKKI records=${records.joinToString(",")}")
    println("AKKI owner=${ChangingService().owner()} audit=${ChangingService().audit()} folded=${ConstantUse().name()}")
    println("AKKI retained=${Unchanged().name()},${Untouched().name()}")
}
