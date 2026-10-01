package io.akki.compiler.jar

internal const val CONTRACT: String = "io/akki/compiler/compat/CompilerCompat"

private const val INTRINSICS: String = "kotlin/jvm/internal/Intrinsics"

internal val ClassShape.delegates: Boolean
    get() = members.keys.any { it.endsWith(":L$CONTRACT;") }

internal fun ClassShape.callsDelegate(operation: String): Boolean =
    methodReferences[operation].orEmpty().any { it is MemberReference && it.owner == CONTRACT && it.signature == operation }

internal fun ClassShape.forwards(operation: String): Boolean =
    callsDelegate(operation) && methodReferences.getValue(operation).all { reference ->
        reference is MemberReference && when (reference.owner) {
            name -> reference.isField && reference.descriptor == "L$CONTRACT;"
            CONTRACT -> reference.signature == operation
            else -> reference.owner == INTRINSICS
        }
    }
