package consumer

import io.akki.log

class SharedService {
    fun handle() {
        log.info { "received from commonMain" }
    }
}
