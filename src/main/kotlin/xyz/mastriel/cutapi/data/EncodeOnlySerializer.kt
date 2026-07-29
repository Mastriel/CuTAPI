package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import kotlin.reflect.*
import kotlin.reflect.full.*

/**
 * Converts an application value into the storage-independent [Variant] tree
 * without requiring support for deserialization.
 *
 * Every [Serializer] is also an encode-only serializer. Use [debugView] when a
 * type needs a partial or otherwise different representation for debugging.
 */
public interface EncodeOnlySerializer<in T> {
    public val descriptor: SerializerDescriptor

    public fun serialize(value: T): SerializeResult
}

/**
 * Supplies the shared debug view associated with a type.
 *
 * Implement this on a companion object so [Identifiable] instances can discover
 * their debug view without an instance-level forwarding property.
 */
public interface DebugViewProvider<T : Any> {
    public fun provideDebugView(): DebugView<T>

    public companion object {
        public fun <T : Any> fromCompanion(type: KClass<T>): DebugView<T> {
            val companion = type.accessibleCompanionObjectInstance()
            require(companion is DebugViewProvider<*>) {
                "Companion object of ${type.qualifiedName} must implement " +
                    DebugViewProvider::class.qualifiedName
            }
            @Suppress("UNCHECKED_CAST")
            return companion.provideDebugView() as DebugView<T>
        }

        public inline fun <reified T : Any> fromCompanion(): DebugView<T> =
            fromCompanion(T::class)
    }
}

/**
 * A tagged, encode-only serializer intended for debug output.
 */
public interface DebugView<T : Any> :
    EncodeOnlySerializer<T>,
    DebugViewProvider<T> {
    public val type: KClass<T>
    public val id: Identifier

    override fun provideDebugView(): DebugView<T> = this
}

public interface EncodeOnlySerializerBuilder<R : Any> {
    /**
     * Inherits the properties produced by another encode-only serializer.
     * May be called more than once.
     */
    public fun <P : Any> extends(
        parent: () -> DebugViewProvider<P>
    ): RepresentationExtension<P>

    public fun <T> property(
        reference: KProperty1<R, T>,
        serializer: EncodeOnlySerializer<T>,
        name: String = reference.name,
        block: DebugPropertyBuilder<T>.() -> Unit = {}
    )

    public fun <T> property(
        name: String,
        serializer: EncodeOnlySerializer<T>,
        getProperty: (R) -> T
    )

    public fun <T> property(
        name: String,
        serializer: EncodeOnlySerializer<T>,
        getProperty: (R) -> T,
        block: DebugPropertyBuilder<T>.() -> Unit
    )
}

private class DebugViewExtension<P : Any>(
    private val provider: () -> DebugViewProvider<P>,
    val configuration: RepresentationExtensionImpl<P>
) {
    fun resolve(): DebugView<*> = provider().provideDebugView()
}

private class ResolvedDebugViewExtension<R : Any>(
    val view: DebugView<*>,
    val representation: InheritedStructuredRepresentation<R>,
    val excludedKeys: Set<StructuredPropertyKey>
)

private class EncodeOnlySerializerBuilderImpl<R : Any>(
    private val type: KClass<R>
) : EncodeOnlySerializerBuilder<R> {
    private val state = StructuredRepresentationBuilderState(type, "Debug view")
    private val parentExtensions: MutableList<DebugViewExtension<*>> = mutableListOf()

    override fun <P : Any> extends(
        parent: () -> DebugViewProvider<P>
    ): RepresentationExtension<P> {
        val configuration = RepresentationExtensionImpl<P>()
        parentExtensions += DebugViewExtension(parent, configuration)
        return configuration
    }

    override fun <T> property(
        reference: KProperty1<R, T>,
        serializer: EncodeOnlySerializer<T>,
        name: String,
        block: DebugPropertyBuilder<T>.() -> Unit
    ) {
        state.addProperty(
            name = name,
            sourceName = reference.name,
            ownerType = reference.accessibleOwnerType("debug property"),
            serializer = serializer,
            getProperty = reference::get
        ).configureDebug(block)
    }

    override fun <T> property(
        name: String,
        serializer: EncodeOnlySerializer<T>,
        getProperty: (R) -> T
    ) {
        addProperty(name, serializer, getProperty)
    }

    override fun <T> property(
        name: String,
        serializer: EncodeOnlySerializer<T>,
        getProperty: (R) -> T,
        block: DebugPropertyBuilder<T>.() -> Unit
    ) {
        addProperty(name, serializer, getProperty).configureDebug(block)
    }

    private fun <T> addProperty(
        name: String,
        serializer: EncodeOnlySerializer<T>,
        getProperty: (R) -> T
    ): StructuredProperty<R, T> =
        state.addProperty(
            name = name,
            sourceName = name,
            ownerType = null,
            serializer = serializer,
            getProperty = getProperty
        )

    fun build(id: Identifier): DebugView<R> {
        val builtType = state.targetType()
        val properties = state.properties
        val builtParentExtensions = parentExtensions.toList()
        val parents: List<ResolvedDebugViewExtension<R>> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val parentViews = resolveStructuredParents(
                type = builtType,
                description = "Debug view",
                providers = builtParentExtensions.map { extension ->
                    { extension.resolve() }
                },
                parentType = { it.type }
            )
            parentViews.zip(builtParentExtensions).map { (parentView, extension) ->
                val excludedKeys = extension.configuration.excludedPropertyKeys()
                val excludedNames = excludedKeys.mapTo(linkedSetOf()) { key ->
                    require(parentView.type.isSubclassOf(key.ownerType)) {
                        "Cannot exclude ${key.ownerType.qualifiedName}.${key.propertyName} " +
                            "from debug view ${parentView.id}"
                    }
                    parentView.serializedName(key)
                        ?: if (parentView.hasStructuredMetadata()) {
                            error(
                                "Debug view ${parentView.id} does not declare property " +
                                    "${key.ownerType.qualifiedName}.${key.propertyName}"
                            )
                        } else {
                            key.propertyName
                        }
                }
                @Suppress("UNCHECKED_CAST")
                ResolvedDebugViewExtension(
                    view = parentView,
                    representation = InheritedStructuredRepresentation(
                        serializer = parentView as EncodeOnlySerializer<R>,
                        excludedNames = excludedNames
                    ),
                    excludedKeys = excludedKeys
                )
            }
        }
        return object : DebugView<R>, StructuredRepresentationMetadata {
            override val type: KClass<R> = builtType
            override val id: Identifier = id
            override val descriptor: SerializerDescriptor.Object
                get() = SerializerDescriptor.Object(
                    id = id,
                    type = builtType,
                    tagged = true,
                    properties = buildList {
                        parents.flatMapTo(this) { parent ->
                            parent.view.descriptor.objectProperties()
                                .filterNot { it.name in parent.representation.excludedNames }
                        }
                        addAll(properties)
                    }
                )

            override fun serializedName(key: StructuredPropertyKey): String? {
                val names = buildList {
                    properties.filter { it.key == key }.mapTo(this) { it.name }
                    parents
                        .filter { key !in it.excludedKeys }
                        .mapNotNullTo(this) { it.view.serializedName(key) }
                }.distinct()
                require(names.size <= 1) {
                    "Debug view $id inherits property ${key.ownerType.qualifiedName}.${key.propertyName} " +
                        "under multiple serialized names: ${names.joinToString()}"
                }
                return names.singleOrNull()
            }

            override fun structuredProperty(name: String): StructuredProperty<*, *>? {
                val matches = buildList {
                    properties.filterTo(this) { it.name == name }
                    parents
                        .filter { name !in it.representation.excludedNames }
                        .mapNotNullTo(this) { it.view.structuredProperty(name) }
                }
                require(matches.size <= 1) {
                    "Debug view $id inherits debug property '$name' more than once"
                }
                return matches.singleOrNull()
            }

            override fun serialize(value: R): SerializeResult =
                serializeStructuredRepresentation(
                    value = value,
                    type = builtType,
                    id = id,
                    tagged = true,
                    properties = properties,
                    parents = { parents.map { it.representation } }
                )
        }
    }
}

private class DeferredDebugView<T : Any>(
    override val type: KClass<T>,
    override val id: Identifier,
    resolve: () -> DebugView<T>
) : DebugView<T>, StructuredRepresentationMetadata {
    private val resolved: DebugView<T> by lazy(LazyThreadSafetyMode.SYNCHRONIZED, resolve)

    override val descriptor: SerializerDescriptor
        get() = resolved.descriptor

    override fun serializedName(key: StructuredPropertyKey): String? =
        (resolved as? StructuredRepresentationMetadata)?.serializedName(key)

    override fun structuredProperty(name: String): StructuredProperty<*, *>? =
        resolved.structuredProperty(name)

    override fun serialize(value: T): SerializeResult = try {
        resolved.serialize(value)
    } catch (exception: Exception) {
        SerializeResult.Failure(exception)
    }
}

private fun SerializerDescriptor.objectProperties(): List<SerializedPropertyDescriptor> = when (this) {
    is SerializerDescriptor.Object -> properties
    is SerializerDescriptor.Polymorphic -> base.properties
    else -> emptyList()
}

private fun DebugView<*>.hasStructuredMetadata(): Boolean =
    this is StructuredRepresentationMetadata || this is Schema<*>

private fun DebugView<*>.serializedName(key: StructuredPropertyKey): String? = when (this) {
    is StructuredRepresentationMetadata -> serializedName(key)
    is Schema<*> -> properties.singleOrNull { it.structuredProperty.key == key }?.name
    else -> null
}

/**
 * Creates a tagged, serialize-only representation for debugging.
 *
 * Debug views are built lazily so they can safely be declared on foundational
 * companion objects. They are not registered and cannot be deserialized.
 */
public inline fun <reified T : Any> debugView(
    id: Identifier,
    noinline block: EncodeOnlySerializerBuilder<T>.() -> Unit
): DebugView<T> = debugView(T::class, id, block)

public fun <T : Any> debugView(
    type: KClass<T>,
    id: Identifier,
    block: EncodeOnlySerializerBuilder<T>.() -> Unit
): DebugView<T> = DeferredDebugView(type, id) {
    EncodeOnlySerializerBuilderImpl(type).apply(block).build(id)
}
