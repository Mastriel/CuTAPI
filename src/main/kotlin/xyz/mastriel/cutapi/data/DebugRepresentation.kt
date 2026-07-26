package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

/**
 * A serialize-only representation used to display an object in debugging tools.
 *
 * Every [Serializer] is also a debug representation. Use [debugView] only when a
 * type needs a partial or otherwise different representation for debugging.
 */
public interface DebugRepresentation<in T> {
    public fun serialize(value: T): SerializeResult
}

public interface DebugRepresentationBuilder<R : Any> {
    /**
     * Inherits the properties produced by another debug representation.
     * May be called more than once.
     */
    public fun <P : Any> extends(parent: () -> DebugRepresentation<P>)

    public fun <T> property(
        reference: KProperty1<R, T>,
        representation: DebugRepresentation<T>,
        name: String = reference.name
    )

    public fun <T> property(
        name: String,
        representation: DebugRepresentation<T>,
        getProperty: (R) -> T
    )
}

private class DebugRepresentationBuilderImpl<R : Any>(
    type: KClass<R>
) : DebugRepresentationBuilder<R> {
    private val state = StructuredRepresentationBuilderState(type, "Debug representation")
    private val parentProviders: MutableList<() -> DebugRepresentation<*>> = mutableListOf()

    override fun <P : Any> extends(parent: () -> DebugRepresentation<P>) {
        parentProviders += parent
    }

    override fun <T> property(
        reference: KProperty1<R, T>,
        representation: DebugRepresentation<T>,
        name: String
    ) {
        state.addProperty(
            name = name,
            sourceName = reference.name,
            ownerType = reference.accessibleOwnerType("debug property"),
            representation = representation,
            getProperty = reference::get
        )
    }

    override fun <T> property(
        name: String,
        representation: DebugRepresentation<T>,
        getProperty: (R) -> T
    ) {
        state.addProperty(
            name = name,
            sourceName = name,
            ownerType = null,
            representation = representation,
            getProperty = getProperty
        )
    }

    fun build(id: Identifier): DebugRepresentation<R> {
        val type = state.targetType()
        val properties = state.properties
        val builtParentProviders = parentProviders.toList()
        return object : DebugRepresentation<R> {
            override fun serialize(value: R): SerializeResult = try {
                val parents = builtParentProviders.map { parentProvider ->
                    @Suppress("UNCHECKED_CAST")
                    parentProvider() as DebugRepresentation<R>
                }
                SerializeResult.Success(
                    encodeStructuredRepresentation(
                        value = value,
                        type = type,
                        id = id,
                        tagged = true,
                        properties = properties,
                        parents = parents
                    )
                )
            } catch (exception: Exception) {
                SerializeResult.Failure(exception)
            }
        }
    }
}

/**
 * Creates a tagged, serialize-only representation for debugging.
 *
 * Debug representations are not registered and cannot be deserialized.
 */
public inline fun <reified T : Any> debugView(
    id: Identifier,
    noinline block: DebugRepresentationBuilder<T>.() -> Unit
): DebugRepresentation<T> = debugView(T::class, id, block)

public fun <T : Any> debugView(
    type: KClass<T>,
    id: Identifier,
    block: DebugRepresentationBuilder<T>.() -> Unit
): DebugRepresentation<T> = DebugRepresentationBuilderImpl(type).apply(block).build(id)
