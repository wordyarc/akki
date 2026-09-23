package io.akki

internal fun fileIntrinsic(): Logger = log

internal fun fileFunctionAnchor(): Logger = logger()

internal fun fileFactoryAnchor(): Logger = Log.forCaller()
