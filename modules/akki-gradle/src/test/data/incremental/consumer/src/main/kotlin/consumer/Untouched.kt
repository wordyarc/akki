package consumer

import io.akki.Log

class Untouched {
    fun name(): String = Log.of<Untouched>().name
}
