// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*
import io.akki.test.RecordingBackend
import io.akki.test.recordLogs

suspend fun pause() {}

suspend fun scoped(): Int {
    LogScope(RecordingBackend()).run { <!NON_LOCAL_SUSPENSION_POINT!>pause<!>() }
    recordLogs { <!NON_LOCAL_SUSPENSION_POINT!>pause<!>() }
    return LogScope(RecordingBackend()).run { 42 }
}
