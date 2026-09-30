package io.akki.test.internal

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class LockFreeAppendList<T> {
    private val head: AtomicReference<Node<T>?> = AtomicReference(null)

    fun toList(): List<T> = generateSequence(head.load()) { it.next }.map { it.value }.toList().asReversed()

    fun add(element: T) {
        while (true) {
            val current = head.load()
            if (head.compareAndSet(current, Node(element, current))) return
        }
    }

    private class Node<T>(val value: T, val next: Node<T>?)
}
