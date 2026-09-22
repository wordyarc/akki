package io.akki

import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal fun captureStderr(block: () -> Unit): String {
    val buffer = ByteArrayOutputStream()
    val original = System.err
    System.setErr(PrintStream(buffer, true))
    try {
        block()
    } finally {
        System.setErr(original)
    }
    return buffer.toString()
}
