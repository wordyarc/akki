// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*
import io.akki.test.recordLogs

suspend fun pause() {}

suspend fun scoped(): Int {
    withLogContext(LogContext("requestId" to 7)) { <!NON_LOCAL_SUSPENSION_POINT!>pause<!>() }
    withLogFields("requestId" to 7) { <!NON_LOCAL_SUSPENSION_POINT!>pause<!>() }
    recordLogs { <!NON_LOCAL_SUSPENSION_POINT!>pause<!>() }
    return withLogContext(LogContext.EMPTY) { 42 }
}
