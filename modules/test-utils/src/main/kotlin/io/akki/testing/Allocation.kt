package io.akki.testing

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

fun allocatedBytes(block: () -> Unit): Long {
    val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
    val before = threads.currentThreadAllocatedBytes
    block()
    return threads.currentThreadAllocatedBytes - before
}
