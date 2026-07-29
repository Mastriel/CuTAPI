package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

public const val SCHEMA_TYPE_DISCRIMINATOR: String = $$"$type"

public class SchemaProperty<R : Any, T> internal constructor(
    public val getProperty: (R) -> T,
    public val setProperty: (R, T) -> Unit,
    public val serializer: Serializer<T>,
    public val name: String,
    internal val propertyName: String,
    internal val ownerType: KClass<*>?,
    internal val isMutable: Boolean
) {
    internal val structuredProperty: StructuredProperty<R, T> = StructuredProperty(
        name = name,
        sourceName = propertyName,
        ownerType = ownerType,
        serializer = serializer,
        getProperty = getProperty
    )

    internal fun deserializeToValue(variant: Variant): DeserializeResult<Any?> =
        when (val result = serializer.deserialize(variant)) {
            is DeserializeResult.Success -> DeserializeResult.Success(result.value)
            is DeserializeResult.Failure -> result
        }

    @Suppress("UNCHECKED_CAST")
    internal fun setOn(instance: R, value: Any?) {
        setProperty(instance, value as T)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <S : Any> forSubtype(): SchemaProperty<S, *> =
        this as SchemaProperty<S, *>
}

public interface SchemaBuilder<R : Any> {
    /** When true, this schema omits the reserved `$type` field and accepts only untagged maps. */
    public var untagged: Boolean

    /** Inherits properties from a parent schema. May be called more than once. */
    public fun <P : Any> extends(
        parent: () -> Schema<P>
    ): RepresentationExtension<P>

    public fun <T> property(
        reference: KProperty1<R, T>,
        serializer: Serializer<T>,
        name: String = reference.name,
        block: DebugPropertyBuilder<T>.() -> Unit = {}
    )

    public fun <T> property(
        name: String,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: (R, T) -> Unit,
        propertyName: String = name
    )

    public fun <T> property(
        name: String,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: (R, T) -> Unit,
        propertyName: String = name,
        block: DebugPropertyBuilder<T>.() -> Unit
    )
}

internal class SchemaExtension<P : Any>(
    private val provider: () -> Schema<P>,
    val configuration: RepresentationExtensionImpl<P>
) {
    fun resolve(): Schema<*> = provider()
}

public interface Schema<T : Any> : TaggedSerializer<T>, DebugView<T> {
    override val type: KClass<T>
    public val properties: List<SchemaProperty<T, *>>
    public val untagged: Boolean

    override val debugView: EncodeOnlySerializer<Schema<*>>
        get() = Companion.provideDebugView()

    override fun provideDebugView(): DebugView<T> = this

    public companion object :
        IdentifierRegistry<Schema<*>>(id("cutapi:registry/schema")),
        DebugViewProvider<Schema<*>> by debugView<Schema<*>>(id("cutapi:schema"), {
            extends { Identifiable }
            property("type", VariantSerializer.String) {
                it.type.qualifiedName ?: "<anonymous class>"
            }
            property(Schema<*>::untagged, VariantSerializer.Boolean)
            property("properties", VariantSerializer.Map) { schema ->
                schema.properties.associate { property ->
                    Variant.String(property.name) to
                        Variant.String(property.serializer.debugTypeName())
                }
            }
        }) {
        internal fun registerSchema(schema: Schema<*>): Schema<*> {
            val registered = getOrNull(schema.id)
            require(registered == null || registered === schema) {
                "A different schema is already registered as ${schema.id}"
            }
            return registered ?: register(schema)
        }
    }
}

private fun Serializer<*>.debugTypeName(): String = when (this) {
    is Identifiable -> id.toString()
    is SerializerJsonMetadata -> expectedType
    else -> "<untagged>"
}

internal fun <T : Any> Schema<T>.requireRegistered(): Schema<T> {
    val registered = Schema.getOrNull(id)
    require(registered != null) {
        "Schema $id is not registered. Register it during plugin startup with " +
            "Schema.modifyRegistry { register(MySchema) }."
    }
    require(registered === this) {
        "Schema $id is registered with a different schema instance"
    }
    return this
}

internal open class SchemaBuilderImpl<R : Any>(
    fixedType: KClass<R>? = null,
    initialProperties: List<SchemaProperty<R, *>> = emptyList(),
    initialUntagged: Boolean = false
) : SchemaBuilder<R> {
    private val mutableProperties: MutableList<SchemaProperty<R, *>> = initialProperties.toMutableList()
    private val state = StructuredRepresentationBuilderState(
        fixedType = fixedType,
        description = "Schema",
        initialProperties = initialProperties.map { it.structuredProperty }
    )

    private var untaggedValue: Boolean = initialUntagged
    private val parentExtensions: MutableList<SchemaExtension<*>> = mutableListOf()
    internal var hasExplicitUntagged: Boolean = false
        private set

    final override var untagged: Boolean
        get() = untaggedValue
        set(value) {
            untaggedValue = value
            hasExplicitUntagged = true
        }

    val properties: List<SchemaProperty<R, *>> get() = mutableProperties.toList()
    val parents: List<SchemaExtension<*>> get() = parentExtensions.toList()

    init {
        require(mutableProperties.map { it.propertyName }.distinct().size == mutableProperties.size) {
            "Inherited schema properties must have unique Kotlin property names"
        }
    }

    final override fun <P : Any> extends(
        parent: () -> Schema<P>
    ): RepresentationExtension<P> {
        val configuration = RepresentationExtensionImpl<P>()
        parentExtensions += SchemaExtension(parent, configuration)
        return configuration
    }

    final override fun <T> property(
        reference: KProperty1<R, T>,
        serializer: Serializer<T>,
        name: String,
        block: DebugPropertyBuilder<T>.() -> Unit
    ) {
        addProperty(
            name = name,
            propertyName = reference.name,
            ownerType = reference.accessibleOwnerType("schema property"),
            isMutable = reference is KMutableProperty1<*, *>,
            serializer = serializer,
            getProperty = reference::get,
            setProperty = { instance, value ->
                val mutableReference = reference as? KMutableProperty1<R, T>
                    ?: error("Schema property '${reference.name}' is not mutable")
                mutableReference.set(instance, value)
            }
        ).structuredProperty.configureDebug(block)
    }

    final override fun <T> property(
        name: String,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: (R, T) -> Unit,
        propertyName: String
    ) {
        addProperty(
            name = name,
            propertyName = propertyName,
            ownerType = null,
            isMutable = true,
            serializer = serializer,
            getProperty = getProperty,
            setProperty = setProperty
        )
    }

    final override fun <T> property(
        name: String,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: (R, T) -> Unit,
        propertyName: String,
        block: DebugPropertyBuilder<T>.() -> Unit
    ) {
        addProperty(
            name = name,
            propertyName = propertyName,
            ownerType = null,
            isMutable = true,
            serializer = serializer,
            getProperty = getProperty,
            setProperty = setProperty
        ).structuredProperty.configureDebug(block)
    }

    private fun <T> addProperty(
        name: String,
        propertyName: String,
        ownerType: KClass<*>?,
        isMutable: Boolean,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: (R, T) -> Unit
    ): SchemaProperty<R, T> {
        require(propertyName.isNotBlank()) { "Schema property Kotlin names cannot be blank" }
        require(mutableProperties.none { it.propertyName == propertyName }) {
            "Schema property '$propertyName' is already registered"
        }

        val property = SchemaProperty(
            getProperty = getProperty,
            setProperty = setProperty,
            serializer = serializer,
            name = name,
            propertyName = propertyName,
            ownerType = ownerType,
            isMutable = isMutable
        )
        state.addProperty(property.structuredProperty)
        mutableProperties += property
        return property
    }

    fun targetType(): KClass<R> = state.targetType()
}

internal abstract class BaseSchema<T : Any>(
    final override val type: KClass<T>,
    final override val id: Identifier,
    final override val properties: List<SchemaProperty<T, *>>,
    final override val untagged: Boolean
) : Schema<T> {
    private val structuredProperties = properties.map { it.structuredProperty }

    init {
        require(properties.map { it.name }.distinct().size == properties.size) {
            "Schema for ${type.qualifiedName} has duplicate serialized property names"
        }
        require(properties.map { it.propertyName }.distinct().size == properties.size) {
            "Schema for ${type.qualifiedName} has duplicate Kotlin property names"
        }
        for (property in properties) {
            require(property.ownerType == null || type.isSubclassOf(property.ownerType)) {
                "Property '${property.propertyName}' cannot be read from ${type.qualifiedName}"
            }
        }
    }

    protected open fun validateSerializableValue(value: T) {}

    final override fun serialize(value: T): SerializeResult =
        serializeStructuredRepresentation(
            value = value,
            type = type,
            id = id,
            tagged = !untagged,
            properties = structuredProperties,
            validate = ::validateSerializableValue
        )

    protected fun validateType(typeValue: Variant?) {
        if (untagged) {
            require(typeValue == null) {
                "Untagged schema ${type.qualifiedName} does not accept '$SCHEMA_TYPE_DISCRIMINATOR'"
            }
            return
        }
        val serializedId = (typeValue as? Variant.String)?.value
            ?: throw DataSerializationException(
                "Schema ${type.qualifiedName} requires string property '$SCHEMA_TYPE_DISCRIMINATOR'"
            )
        require(serializedId == id.toString()) {
            "Schema ${type.qualifiedName} cannot deserialize type '$serializedId'; expected '$id'"
        }
    }

    protected fun valuesForDeserialization(variant: Variant): LinkedHashMap<String, Variant> {
        val values = variant.stringValues()
        validateType(values.remove(SCHEMA_TYPE_DISCRIMINATOR))

        val unknownNames = values.keys - properties.mapTo(mutableSetOf()) { it.name }
        require(unknownNames.isEmpty()) {
            "Unknown properties for ${type.qualifiedName}: ${unknownNames.joinToString()}"
        }
        return values
    }

    protected fun deserializeProperty(
        property: SchemaProperty<T, *>,
        variant: Variant
    ): Any? = when (val result = property.deserializeToValue(variant)) {
        is DeserializeResult.Success -> result.value
        is DeserializeResult.Failure -> throw DataSerializationException(
            "Failed to deserialize '${property.name}' for ${type.qualifiedName}",
            result.error
        )
    }
}

internal class SchemaImpl<T : Any>(
    type: KClass<T>,
    id: Identifier,
    properties: List<SchemaProperty<T, *>>,
    untagged: Boolean
) : BaseSchema<T>(type, id, properties, untagged) {
    private data class Binding<R : Any>(
        val property: SchemaProperty<R, *>,
        val parameter: KParameter?
    )

    private val constructor: KFunction<T>? = type.primaryConstructor
    private val bindings: List<Binding<T>>

    init {
        val concreteConstructor = constructor
        bindings = if (concreteConstructor == null) {
            emptyList()
        } else {
            concreteConstructor.isAccessible = true
            val parameters = concreteConstructor.parameters
                .filter { it.kind == KParameter.Kind.VALUE }
                .associateBy { it.name }
            val boundBindings = properties.map { property ->
                val parameter = parameters[property.propertyName]
                require(parameter != null || property.isMutable) {
                    "Property '${property.propertyName}' must be a constructor parameter or a mutable property"
                }
                Binding(property, parameter)
            }

            val boundParameters = boundBindings.mapNotNull { it.parameter }.toSet()
            val missingParameters = parameters.values.filter { !it.isOptional && it !in boundParameters }
            require(missingParameters.isEmpty()) {
                "Schema for ${type.qualifiedName} is missing constructor properties: " +
                    missingParameters.joinToString { it.name ?: "<unnamed>" }
            }
            boundBindings
        }
    }

    override fun deserialize(variant: Variant): DeserializeResult<T> = try {
        val concreteConstructor = constructor
            ?: throw DataSerializationException(
                "Schema type ${type.qualifiedName} cannot be deserialized because it has no primary constructor"
            )
        val values = valuesForDeserialization(variant)

        val arguments = mutableMapOf<KParameter, Any?>()
        val valuesForSetters = mutableListOf<Pair<SchemaProperty<T, *>, Any?>>()
        for (binding in bindings) {
            val property = binding.property
            val encoded = values[property.name]
            if (encoded == null) {
                require(binding.parameter?.isOptional == true || binding.parameter == null) {
                    "Missing property '${property.name}' for ${type.qualifiedName}"
                }
                continue
            }

            val decoded = deserializeProperty(property, encoded)
            if (binding.parameter != null) {
                arguments[binding.parameter] = decoded
            } else {
                valuesForSetters += property to decoded
            }
        }

        val instance = concreteConstructor.callBy(arguments)
        for ((property, value) in valuesForSetters) property.setOn(instance, value)
        DeserializeResult.Success(instance)
    } catch (exception: Exception) {
        DeserializeResult.Failure(exception)
    }

}

internal class SingletonSchemaImpl<T : Any>(
    type: KClass<T>,
    id: Identifier,
    properties: List<SchemaProperty<T, *>>,
    untagged: Boolean
) : BaseSchema<T>(type, id, properties, untagged) {
    private val instance: T
        get() = type.singletonObjectInstance()
            ?: error("Singleton schema $id requires ${type.qualifiedName} to be a Kotlin object")

    init {
        for (property in properties) {
            require(property.isMutable) {
                "Singleton schema property '${property.propertyName}' must be mutable"
            }
        }
    }

    override fun validateSerializableValue(value: T) {
        require(value === instance) {
            "Singleton schema $id can only serialize its Kotlin object instance"
        }
    }

    override fun deserialize(variant: Variant): DeserializeResult<T> = try {
        val values = valuesForDeserialization(variant)

        val singleton = instance
        for (property in properties) {
            val encoded = values[property.name]
            require(encoded != null) { "Missing property '${property.name}' for ${type.qualifiedName}" }
            val decoded = deserializeProperty(property, encoded)
            property.setOn(singleton, decoded)
        }
        DeserializeResult.Success(singleton)
    } catch (exception: Exception) {
        DeserializeResult.Failure(exception)
    }

}

private class DeferredSchema<T : Any>(
    override val type: KClass<T>,
    override val id: Identifier,
    resolve: () -> Schema<T>
) : Schema<T> {
    private val resolved: Schema<T> by lazy(LazyThreadSafetyMode.SYNCHRONIZED, resolve)

    override val properties: List<SchemaProperty<T, *>> get() = resolved.properties
    override val untagged: Boolean get() = resolved.untagged

    override fun serialize(value: T): SerializeResult = resolved.serialize(value)

    override fun deserialize(variant: Variant): DeserializeResult<T> = resolved.deserialize(variant)
}

/**
 * Creates a schema without registering it.
 *
 * Register schemas during plugin startup:
 * ```kt
 * Schema.modifyRegistry {
 *     register(MyType)
 * }
 * ```
 */
public inline fun <reified T : Any> schema(
    id: Identifier,
    noinline block: SchemaBuilder<T>.() -> Unit
): Schema<T> = schema(T::class, id, block)

public fun <T : Any> schema(
    type: KClass<T>,
    id: Identifier,
    block: SchemaBuilder<T>.() -> Unit
): Schema<T> {
    val builder = SchemaBuilderImpl(type).apply(block)
    return builder.build(id)
}

/**
 * Creates an unregistered schema for a Kotlin object.
 *
 * Register the returned schema with [Schema.modifyRegistry] during plugin startup.
 */
public inline fun <reified T : Any> singletonSchema(
    id: Identifier,
    noinline block: SchemaBuilder<T>.() -> Unit = {}
): Schema<T> = singletonSchema(T::class, id, block)

public fun <T : Any> singletonSchema(
    type: KClass<T>,
    id: Identifier,
    block: SchemaBuilder<T>.() -> Unit = {}
): Schema<T> {
    val builder = SchemaBuilderImpl(type).apply(block)
    return builder.buildSingleton(id)
}

internal fun <T : Any> SchemaBuilderImpl<T>.build(id: Identifier): Schema<T> {
    return buildSchema(id) { type, schemaId, schemaProperties, schemaUntagged ->
        SchemaImpl(type, schemaId, schemaProperties, schemaUntagged)
    }
}

internal fun <T : Any> SchemaBuilderImpl<T>.buildSingleton(id: Identifier): Schema<T> {
    return buildSchema(id) { type, schemaId, schemaProperties, schemaUntagged ->
        SingletonSchemaImpl(type, schemaId, schemaProperties, schemaUntagged)
    }
}

private fun <T : Any> SchemaBuilderImpl<T>.buildSchema(
    id: Identifier,
    create: (KClass<T>, Identifier, List<SchemaProperty<T, *>>, Boolean) -> Schema<T>
): Schema<T> {
    val childType = targetType()
    if (parents.isEmpty()) return create(childType, id, properties, untagged)

    return DeferredSchema(childType, id) {
        val parentSchemas = resolveStructuredParents(
            type = childType,
            description = "Schema",
            providers = parents.map { extension ->
                { extension.resolve() }
            },
            parentType = { it.type }
        )
        val inherited = parentSchemas.zip(parents).flatMap { (parentSchema, extension) ->
            val excludedKeys = extension.configuration.excludedPropertyKeys()
            for (key in excludedKeys) {
                require(parentSchema.type.isSubclassOf(key.ownerType)) {
                    "Cannot exclude ${key.ownerType.qualifiedName}.${key.propertyName} " +
                        "from schema ${parentSchema.id}"
                }
                require(parentSchema.properties.any { it.structuredProperty.key == key }) {
                    "Schema ${parentSchema.id} does not declare property " +
                        "${key.ownerType.qualifiedName}.${key.propertyName}"
                }
            }
            parentSchema.properties
                .filterNot { it.structuredProperty.key in excludedKeys }
                .map { it.forSubtype<T>() }
        }
        val combinedProperties = inherited + properties
        val childUntagged = if (hasExplicitUntagged) {
            untagged
        } else {
            parentSchemas.all { it.untagged }
        }
        create(childType, id, combinedProperties, childUntagged)
    }
}

internal fun Variant.stringValues(): LinkedHashMap<String, Variant> {
    val map = this as? Variant.Map ?: throw VariantTypeException("Map", this)
    val result = linkedMapOf<String, Variant>()
    for ((key, value) in map.value) {
        val name = (key as? Variant.String)?.value
            ?: throw DataSerializationException("Schema maps require string keys")
        result[name] = value
    }
    return result
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> KClass<T>.singletonObjectInstance(): T? {
    return try {
        objectInstance
    } catch (_: IllegalAccessException) {
        java.getDeclaredField("INSTANCE").also { it.isAccessible = true }.get(null) as? T
    }
}
