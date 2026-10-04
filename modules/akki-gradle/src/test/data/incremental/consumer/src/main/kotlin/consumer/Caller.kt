package consumer

import library.Handler
import library.capturing
import library.traced

class Caller {
    fun call(): String = traced()

    fun anonymous(): Handler = capturing { "caller" }
}
