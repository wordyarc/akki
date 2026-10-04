package io.akki.compiler.jar

internal const val CONTRACT: String = "io/akki/compiler/compat/CompilerCompat"

private const val INTRINSICS: String = "kotlin/jvm/internal/Intrinsics"

internal fun adapterChain(
    adapter: Adapter,
    adapters: List<Adapter>,
    shapes: Map<String, ClassShape>,
): List<Adapter> = generateSequence(adapter) { current ->
    val constructors = shapes.getValue(current.implementation.internalName).references()
        .filterIsInstance<MemberReference>()
        .filter { it.name == "<init>" }
        .mapTo(mutableSetOf()) { it.owner }
    val delegates = adapters.filter { it.factory.internalName in constructors }
    require(delegates.size <= 1) { "$current creates several compiler adapters: $delegates" }
    val delegate = delegates.singleOrNull() ?: return@generateSequence null
    require(adapters.indexOf(delegate) < adapters.indexOf(current)) {
        "$current must delegate to an older adapter: $delegate"
    }
    delegate
}.toList()

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
