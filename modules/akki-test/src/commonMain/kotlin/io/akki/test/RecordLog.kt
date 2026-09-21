package io.akki.test

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class RecordLog<T> {
    private val head: AtomicReference<Node<T>?> = AtomicReference(null)

    val snapshot: List<T>
        get() = generateSequence(head.load()) { it.next }.map { it.value }.toList().asReversed()

    fun add(entry: T) {
        while (true) {
            val current = head.load()
            if (head.compareAndSet(current, Node(entry, current))) return
        }
    }

    private class Node<T>(val value: T, val next: Node<T>?)
}
