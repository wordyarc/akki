// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*

<!NOTHING_TO_INLINE!>inline<!> fun viaIntrinsic(): String = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name

<!NOTHING_TO_INLINE!>inline<!> fun viaAnchor(): String = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>logger()<!>.name

<!NOTHING_TO_INLINE!>inline<!> fun viaFactory(): String = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>Log.forCaller()<!>.name

inline fun withLambdaParameter(body: () -> Unit): String {
    body()
    return <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name
}

inline val viaInlineProperty: String get() = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name

val viaInlineGetter: String inline get() = <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name

<!NOTHING_TO_INLINE!>inline<!> fun withNoinlineDefault(noinline probe: () -> String = { <!CONTEXTUAL_LOGGER_IN_INLINE_DECLARATION!>log<!>.name }): String = probe()
