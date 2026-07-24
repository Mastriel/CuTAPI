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
    internal fun serializeFrom(value: R): SerializeResult = serializer.serialize(getProperty(value))

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
    public fun <P : Any> extends(parent: () -> Schema<P>)

    public fun <T> property(
        reference: KProperty1<R, T>,
        serializer: Serializer<T>,
        name: String = reference.name
    )

    public fun <T> property(
        name: String,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: (R, T) -> Unit,
        propertyName: String = name
    )
}

public interface Schema<T : Any> : TaggedSerializer<T> {
    public val type: KClass<T>
    public val properties: List<SchemaProperty<T, *>>
    public val untagged: Boolean

    public companion object : IdentifierRegistry<Schema<*>>(id("cutapi:registry/schema")) {
        public fun registerSchema(schema: Schema<*>): Schema<*> {
            if (has(schema.id)) return get(schema.id)
            return register(schema)
        }
    }
}

internal open class SchemaBuilderImpl<R : Any>(
    private val fixedType: KClass<R>? = null,
    initialProperties: List<SchemaProperty<R, *>> = emptyList(),
    initialUntagged: Boolean = false
) : SchemaBuilder<R> {
    private val mutableProperties: MutableList<SchemaProperty<R, *>> = initialProperties.toMutableList()
    private var inferredType: KClass<*>? = fixedType

    private var untaggedValue: Boolean = initialUntagged
    private val parentProviders: MutableList<() -> Schema<*>> = mutableListOf()
    internal var hasExplicitUntagged: Boolean = false
        private set

    final override var untagged: Boolean
        get() = untaggedValue
        set(value) {
            untaggedValue = value
            hasExplicitUntagged = true
        }

    val properties: List<SchemaProperty<R, *>> get() = mutableProperties.toList()
    val parents: List<() -> Schema<*>> get() = parentProviders.toList()

    init {
        require(mutableProperties.map { it.name }.distinct().size == mutableProperties.size) {
            "Inherited schema properties must have unique serialized names"
        }
        require(mutableProperties.map { it.propertyName }.distinct().size == mutableProperties.size) {
            "Inherited schema properties must have unique Kotlin property names"
        }
    }

    final override fun <P : Any> extends(parent: () -> Schema<P>) {
        parentProviders += parent
    }

    final override fun <T> property(
        reference: KProperty1<R, T>,
        serializer: Serializer<T>,
        name: String
    ) {
        reference.isAccessible = true
        val ownerType = reference.parameters.firstOrNull()?.type?.classifier as? KClass<*>
            ?: error("Cannot determine the owner of schema property '${reference.name}'")
        addProperty(
            name = name,
            propertyName = reference.name,
            ownerType = ownerType,
            isMutable = reference is KMutableProperty1<*, *>,
            serializer = serializer,
            getProperty = reference::get,
            setProperty = { instance, value ->
                val mutableReference = reference as? KMutableProperty1<R, T>
                    ?: error("Schema property '${reference.name}' is not mutable")
                mutableReference.set(instance, value)
            }
        )
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

    private fun <T> addProperty(
        name: String,
        propertyName: String,
        ownerType: KClass<*>?,
        isMutable: Boolean,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: (R, T) -> Unit
    ) {
        require(name.isNotBlank()) { "Schema property names cannot be blank" }
        require(propertyName.isNotBlank()) { "Schema property Kotlin names cannot be blank" }
        require(name != SCHEMA_TYPE_DISCRIMINATOR) { "'$SCHEMA_TYPE_DISCRIMINATOR' is reserved for schema type information" }
        require(mutableProperties.none { it.name == name }) {
            "Schema property name '$name' is already registered"
        }
        require(mutableProperties.none { it.propertyName == propertyName }) {
            "Schema property '$propertyName' is already registered"
        }

        val targetType = fixedType
        if (targetType != null && ownerType != null) {
            require(targetType.isSubclassOf(ownerType)) {
                "Property '$propertyName' cannot be read from ${targetType.qualifiedName}"
            }
        } else if (ownerType != null) {
            val currentType = inferredType
            inferredType = when {
                currentType == null -> ownerType
                currentType.isSubclassOf(ownerType) -> currentType
                ownerType.isSubclassOf(currentType) -> ownerType
                else -> error(
                    "Schema properties '${mutableProperties.lastOrNull()?.propertyName}' and " +
                        "'$propertyName' do not share a compatible owner type"
                )
            }
        }

        mutableProperties += SchemaProperty(
            getProperty = getProperty,
            setProperty = setProperty,
            serializer = serializer,
            name = name,
            propertyName = propertyName,
            ownerType = ownerType,
            isMutable = isMutable
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun targetType(): KClass<R> = (fixedType ?: inferredType) as? KClass<R>
        ?: error("Cannot infer an empty schema's type; use the KClass overload")
}

internal class SchemaImpl<T : Any>(
    override val type: KClass<T>,
    override val id: Identifier,
    override val properties: List<SchemaProperty<T, *>>,
    override val untagged: Boolean
) : Schema<T> {
    private data class Binding<R : Any>(
        val property: SchemaProperty<R, *>,
        val parameter: KParameter?
    )

    private val constructor: KFunction<T>? = type.primaryConstructor
    private val bindings: List<Binding<T>>

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

        val concreteConstructor = constructor
        bindings = if (concreteConstructor == null) {
            emptyList()
        } else {
            require(concreteConstructor.visibility == KVisibility.PUBLIC) {
                "Primary constructor of ${type.qualifiedName} must be public"
            }
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

    override fun serialize(value: T): SerializeResult = try {
        val encoded = linkedMapOf<Variant, Variant>()
        if (!untagged) encoded[Variant.String(SCHEMA_TYPE_DISCRIMINATOR)] = Variant.String(id.toString())
        for (property in properties) {
            when (val result = property.serializeFrom(value)) {
                is SerializeResult.Success -> encoded[Variant.String(property.name)] = result.value
                is SerializeResult.Failure -> throw DataSerializationException(
                    "Failed to serialize '${property.name}' from ${type.qualifiedName}",
                    result.error
                )
            }
        }
        SerializeResult.Success(Variant.Map(encoded))
    } catch (exception: Exception) {
        SerializeResult.Failure(exception)
    }

    override fun deserialize(variant: Variant): DeserializeResult<T> = try {
        val concreteConstructor = constructor
            ?: throw DataSerializationException(
                "Schema type ${type.qualifiedName} cannot be deserialized because it has no primary constructor"
            )
        val values = variant.stringValues()
        validateType(values.remove(SCHEMA_TYPE_DISCRIMINATOR))

        val propertiesByName = properties.associateBy { it.name }
        val unknownNames = values.keys - propertiesByName.keys
        require(unknownNames.isEmpty()) {
            "Unknown properties for ${type.qualifiedName}: ${unknownNames.joinToString()}"
        }

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

            val decoded = when (val result = property.deserializeToValue(encoded)) {
                is DeserializeResult.Success -> result.value
                is DeserializeResult.Failure -> throw DataSerializationException(
                    "Failed to deserialize '${property.name}' for ${type.qualifiedName}",
                    result.error
                )
            }
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

    private fun validateType(typeValue: Variant?) {
        if (untagged) {
            require(typeValue == null) { "Untagged schema ${type.qualifiedName} does not accept '$SCHEMA_TYPE_DISCRIMINATOR'" }
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
}

internal class SingletonSchemaImpl<T : Any>(
    override val type: KClass<T>,
    override val id: Identifier,
    override val properties: List<SchemaProperty<T, *>>,
    override val untagged: Boolean
) : Schema<T> {
    private val instance: T
        get() = type.singletonObjectInstance()
            ?: error("Singleton schema $id requires ${type.qualifiedName} to be a Kotlin object")

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
            require(property.isMutable) {
                "Singleton schema property '${property.propertyName}' must be mutable"
            }
        }
    }

    override fun serialize(value: T): SerializeResult = try {
        require(value === instance) {
            "Singleton schema $id can only serialize its Kotlin object instance"
        }
        val encoded = linkedMapOf<Variant, Variant>()
        if (!untagged) encoded[Variant.String(SCHEMA_TYPE_DISCRIMINATOR)] = Variant.String(id.toString())
        for (property in properties) {
            when (val result = property.serializeFrom(value)) {
                is SerializeResult.Success -> encoded[Variant.String(property.name)] = result.value
                is SerializeResult.Failure -> throw DataSerializationException(
                    "Failed to serialize '${property.name}' from ${type.qualifiedName}",
                    result.error
                )
            }
        }
        SerializeResult.Success(Variant.Map(encoded))
    } catch (exception: Exception) {
        SerializeResult.Failure(exception)
    }

    override fun deserialize(variant: Variant): DeserializeResult<T> = try {
        val values = variant.stringValues()
        validateType(values.remove(SCHEMA_TYPE_DISCRIMINATOR))

        val propertiesByName = properties.associateBy { it.name }
        val unknownNames = values.keys - propertiesByName.keys
        require(unknownNames.isEmpty()) {
            "Unknown properties for ${type.qualifiedName}: ${unknownNames.joinToString()}"
        }

        val singleton = instance
        for (property in properties) {
            val encoded = values[property.name]
            require(encoded != null) { "Missing property '${property.name}' for ${type.qualifiedName}" }
            val decoded = when (val result = property.deserializeToValue(encoded)) {
                is DeserializeResult.Success -> result.value
                is DeserializeResult.Failure -> throw DataSerializationException(
                    "Failed to deserialize '${property.name}' for ${type.qualifiedName}",
                    result.error
                )
            }
            property.setOn(singleton, decoded)
        }
        DeserializeResult.Success(singleton)
    } catch (exception: Exception) {
        DeserializeResult.Failure(exception)
    }

    private fun validateType(typeValue: Variant?) {
        if (untagged) {
            require(typeValue == null) { "Untagged schema ${type.qualifiedName} does not accept '$SCHEMA_TYPE_DISCRIMINATOR'" }
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
    return builder.build(id).also { Schema.registerSchema(it) }
}

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
    return builder.buildSingleton(id).also { Schema.registerSchema(it) }
}

internal fun <T : Any> SchemaBuilderImpl<T>.build(id: Identifier): Schema<T> {
    val childType = targetType()
    if (parents.isEmpty()) return SchemaImpl(childType, id, properties, untagged)

    return DeferredSchema(childType, id) {
        val parentSchemas = parents.map { it() }
        require(parentSchemas.map { it.type }.distinct().size == parentSchemas.size) {
            "Schema for ${childType.qualifiedName} extends the same parent type more than once"
        }
        for (parentSchema in parentSchemas) {
            val parentType = parentSchema.type
            require(childType != parentType && childType.isSubclassOf(parentType)) {
                "Extended schema type ${childType.qualifiedName} must inherit ${parentType.qualifiedName}"
            }
        }
        val inherited = parentSchemas.flatMap { parentSchema ->
            parentSchema.properties.map { it.forSubtype<T>() }
        }
        val combinedProperties = inherited + properties
        val childUntagged = if (hasExplicitUntagged) {
            untagged
        } else {
            parentSchemas.all { it.untagged }
        }
        SchemaImpl(childType, id, combinedProperties, childUntagged)
    }
}

internal fun <T : Any> SchemaBuilderImpl<T>.buildSingleton(id: Identifier): Schema<T> {
    val childType = targetType()
    if (parents.isEmpty()) return SingletonSchemaImpl(childType, id, properties, untagged)

    return DeferredSchema(childType, id) {
        val parentSchemas = parents.map { it() }
        require(parentSchemas.map { it.type }.distinct().size == parentSchemas.size) {
            "Schema for ${childType.qualifiedName} extends the same parent type more than once"
        }
        for (parentSchema in parentSchemas) {
            val parentType = parentSchema.type
            require(childType != parentType && childType.isSubclassOf(parentType)) {
                "Extended schema type ${childType.qualifiedName} must inherit ${parentType.qualifiedName}"
            }
        }
        val inherited = parentSchemas.flatMap { parentSchema ->
            parentSchema.properties.map { it.forSubtype<T>() }
        }
        val combinedProperties = inherited + properties
        val childUntagged = if (hasExplicitUntagged) {
            untagged
        } else {
            parentSchemas.all { it.untagged }
        }
        SingletonSchemaImpl(childType, id, combinedProperties, childUntagged)
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
