package io.akki.test

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class RecordLog<T> {
    private val entries: AtomicReference<List<T>> = AtomicReference(emptyList())

    val snapshot: List<T>
        get() = Snapshot(entries.load())

    fun add(entry: T) {
        while (true) {
            val current = entries.load()
            if (entries.compareAndSet(current, current + entry)) return
        }
    }

    private class Snapshot<T>(private val entries: List<T>) : AbstractList<T>() {
        override val size: Int get() = entries.size

        override fun get(index: Int): T = entries[index]
    }
}
