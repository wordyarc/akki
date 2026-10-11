package io.akki.testing

import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun captureStderr(block: () -> Unit): String {
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
