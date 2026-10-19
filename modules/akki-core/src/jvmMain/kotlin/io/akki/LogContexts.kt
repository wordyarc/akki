@file:JvmName("LogContexts")

package io.akki

import java.util.concurrent.Callable

public fun LogContext.wrap(task: Runnable): Runnable = Runnable { withLogContext(this) { task.run() } }

public fun <T> LogContext.wrap(task: Callable<T>): Callable<T> = Callable { withLogContext(this) { task.call() } }

@JvmSynthetic
public fun <T> LogContext.wrap(task: () -> T): () -> T = { withLogContext(this) { task() } }
