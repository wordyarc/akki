package io.akki.internal

import io.akki.InternalAkkiApi

@InternalAkkiApi
public class AkkiConfigurationException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
