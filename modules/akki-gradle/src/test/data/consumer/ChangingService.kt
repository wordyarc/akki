package consumer

import io.akki.Log
import io.akki.log

class ChangingService {
    fun owner(): String = "before"

    fun audit(): String = Log.named("audit.before").name
}
