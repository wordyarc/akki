// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*

<!NOTHING_TO_INLINE!>inline<!> fun viaIntrinsic(): String = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name

<!NOTHING_TO_INLINE!>inline<!> fun viaAnchor(): String = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>logger()<!>.name

inline fun withLambdaParameter(body: () -> Unit): String {
    body()
    return <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name
}

inline val viaInlineProperty: String get() = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name

val viaInlineGetter: String inline get() = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name

<!NOTHING_TO_INLINE!>inline<!> fun withNoinlineDefault(noinline probe: () -> String = { <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name }): String = probe()

internal inline fun <reified T> moduleWide(): String = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name + T::class.simpleName

open class Base {
    protected inline fun <reified T> forSubclasses(): String = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name + T::class.simpleName
}

private inline fun <reified T> fileLocal(): String = log.name + T::class.simpleName

private inline val viaPrivateProperty: String get() = log.name

class Service {
    private inline fun <reified T> parse(): String = log.name + T::class.simpleName

    fun handle(): String = parse<Int>() + fileLocal<Int>() + viaPrivateProperty
}

private class Hidden {
    inline fun <reified T> exposed(): String = log.name + T::class.simpleName
}

fun viaObjectExpression(): String {
    val probe = object {
        <!NOTHING_TO_INLINE!>inline<!> fun name(): String = log.name
    }
    return probe.name()
}

fun viaLocalClass(): String {
    class Local {
        <!NOTHING_TO_INLINE!>inline<!> fun name(): String = log.name
    }
    return Local().name()
}

<!NOTHING_TO_INLINE!>inline<!> fun objectInPublicInline(): String {
    val probe = object {
        <!NOTHING_TO_INLINE!>inline<!> fun name(): String = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name
    }
    return probe.name()
}
