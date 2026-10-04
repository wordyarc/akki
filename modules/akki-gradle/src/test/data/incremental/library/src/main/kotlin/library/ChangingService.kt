package library

import io.akki.Log
import io.akki.log

class ChangingService {
    fun owner(): String = "before"

    fun audit(): String = Log.named(CATEGORY).name
}
