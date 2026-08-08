package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

public const val SCHEMA_TYPE_DISCRIMINATOR: String = $$"$type"

public class SchemaProperty<R : Any, T> internal constructor(
    public val getProperty: (R) -> T,
    public val setProperty: ((R, T) -> Unit)?,
    public val serializer: Serializer<T>,
    override val name: String,
    override val sourceName: String?,
    override val constructorParameterName: String?,
    internal val ownerType: KClass<*>?,
    constructorParameter: KParameter? = null,
    internal val origin: Any = Any()
) : SchemaPropertyDescriptor {
    private var constructorParameterValue: KParameter? = constructorParameter

    override val constructorParameter: KParameter?
        get() = constructorParameterValue
    override val serializerDescriptor: SerializerDescriptor<*>
        get() = serializer.descriptor

    override var presence: SchemaPropertyPresence<T> = SchemaPropertyPresence.Required
        private set

    override val writable: Boolean
        get() = setProperty != null

    private var presenceConfigured: Boolean = false

    internal val structuredProperty: StructuredProperty<R, T> = StructuredProperty(
        name = name,
        sourceName = sourceName ?: name,
        ownerType = ownerType,
        serializer = serializer,
        getProperty = getProperty,
        shouldSerialize = { value -> shouldSerialize(getProperty(value)) }
    )

    internal fun deserializeToValue(variant: Variant): DeserializeResult<Any?> =
        when (val result = serializer.deserialize(variant)) {
            is DeserializeResult.Success -> DeserializeResult.Success(result.value)
            is DeserializeResult.Failure -> result
        }

    @Suppress("UNCHECKED_CAST")
    internal fun setOn(instance: R, value: Any?) {
        val setter = setProperty
            ?: error("Schema property '$name' cannot be assigned after construction")
        setter(instance, value as T)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <S : Any> forSubtype(): SchemaProperty<S, *> {
        val copied = SchemaProperty(
            getProperty = { instance -> getProperty(instance as R) },
            setProperty = setProperty?.let { setter ->
                { instance: S, value: T -> setter(instance as R, value) }
            },
            serializer = serializer,
            name = name,
            sourceName = sourceName,
            constructorParameterName = constructorParameterName,
            ownerType = ownerType,
            origin = origin
        )
        copied.presence = presence
        copied.presenceConfigured = presenceConfigured
        structuredProperty.copyDebugFormatterTo(copied.structuredProperty)
        return copied
    }

    internal fun bindConstructor(type: KClass<R>): SchemaProperty<R, T> {
        val parameter = constructorParameterName?.let { parameterName ->
            type.primaryConstructor
                ?.parameters
                ?.singleOrNull { it.kind == KParameter.Kind.VALUE && it.name == parameterName }
        }
        if (constructorParameterValue == null) {
            constructorParameterValue = parameter
        }
        val copied = SchemaProperty(
            getProperty = getProperty,
            setProperty = setProperty,
            serializer = serializer,
            name = name,
            sourceName = sourceName,
            constructorParameterName = constructorParameterName,
            ownerType = ownerType,
            constructorParameter = parameter,
            origin = origin
        )
        copied.presence = presence
        copied.presenceConfigured = presenceConfigured
        structuredProperty.copyDebugFormatterTo(copied.structuredProperty)
        return copied
    }

    internal fun configure(block: SchemaPropertyBuilder<R, T>.() -> Unit) {
        object : SchemaPropertyBuilder<R, T> {
            override fun required() {
                configurePresence(SchemaPropertyPresence.Required)
            }

            override fun optional() {
                configurePresence(SchemaPropertyPresence.Optional(false, null))
            }

            override fun optional(omitDefaults: Boolean, default: () -> T) {
                configurePresence(SchemaPropertyPresence.Optional(omitDefaults, default))
            }

            override fun debugFormatter(
                formatter: DebugFormatterContext<T>.() -> net.kyori.adventure.text.Component
            ) {
                structuredProperty.setDebugFormatter(formatter)
            }

            private fun configurePresence(value: SchemaPropertyPresence<T>) {
                require(!presenceConfigured) {
                    "Presence for schema property '$name' is already configured"
                }
                presenceConfigured = true
                presence = value
            }
        }.apply(block)
    }

    private fun shouldSerialize(value: T): Boolean {
        val optional = presence as? SchemaPropertyPresence.Optional<T> ?: return true
        if (!optional.omitDefaults || !optional.hasDefault) return true
        return value != optional.defaultValue()
    }
}

public interface SchemaBuilder<R : Any> {
    /** When true, this schema omits the reserved `$type` field and accepts only untagged maps. */
    public var untagged: Boolean

    /** Inherits properties from a parent schema. May be called more than once. */
    public fun <P : Any> extends(
        parent: () -> Schema<P>
    ): RepresentationExtension<P>

    /**
     * Overrides inferred primary-constructor construction.
     *
     * Values read through [SchemaConstructionContext.value] are consumed by the
     * factory. Remaining decoded values are applied through property setters.
     */
    public fun constructs(
        factory: SchemaConstructionContext<R>.() -> R
    )

    /** Registers a Kotlin property and returns its typed schema handle. */
    public fun <T> property(
        reference: KProperty1<R, T>,
        serializer: Serializer<T>,
        name: String = reference.name,
        constructorParameterName: String? = reference.name,
        block: SchemaPropertyBuilder<R, T>.() -> Unit = {}
    ): SchemaProperty<R, T>

    /** Registers an accessor-backed property and returns its typed schema handle. */
    public fun <T> property(
        name: String,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: ((R, T) -> Unit)? = null,
        constructorParameterName: String? = name,
        block: SchemaPropertyBuilder<R, T>.() -> Unit = {}
    ): SchemaProperty<R, T>
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

    override val descriptor: SerializerDescriptor<SerializerShape.ObjectLike>
        get() = SerializerDescriptor.`object`(
            id = id,
            type = type,
            tagged = !untagged,
            properties = properties
        )

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
                        Variant.String(property.serializer.descriptor.id.toString())
                }
            }
        }) {
        protected override fun register(item: Schema<*>): Schema<*> {
            item.requireDescriptorIdentity()
            return super.register(item)
        }

        internal fun registerSchema(schema: Schema<*>): Schema<*> {
            schema.requireDescriptorIdentity()
            val registered = getOrNull(schema.id)
            require(registered == null || registered === schema) {
                "A different schema is already registered as ${schema.id}"
            }
            return registered ?: register(schema)
        }
    }
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
    private var constructionFactoryValue: (SchemaConstructionContext<R>.() -> R)? = null
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
    val constructionFactory: (SchemaConstructionContext<R>.() -> R)?
        get() = constructionFactoryValue

    init {
        require(
            mutableProperties
                .mapNotNull { property ->
                    property.sourceName?.let { StructuredPropertyKey(property.ownerType ?: return@let null, it) }
                }
                .distinct()
                .size == mutableProperties.count { it.sourceName != null && it.ownerType != null }
        ) {
            "Inherited schema properties must have unique Kotlin property references"
        }
    }

    final override fun <P : Any> extends(
        parent: () -> Schema<P>
    ): RepresentationExtension<P> {
        val configuration = RepresentationExtensionImpl<P>()
        parentExtensions += SchemaExtension(parent, configuration)
        return configuration
    }

    final override fun constructs(factory: SchemaConstructionContext<R>.() -> R) {
        require(constructionFactoryValue == null) {
            "Schema construction can only be configured once"
        }
        constructionFactoryValue = factory
    }

    final override fun <T> property(
        reference: KProperty1<R, T>,
        serializer: Serializer<T>,
        name: String,
        constructorParameterName: String?,
        block: SchemaPropertyBuilder<R, T>.() -> Unit
    ): SchemaProperty<R, T> =
        addProperty(
            name = name,
            sourceName = reference.name,
            constructorParameterName = constructorParameterName,
            ownerType = reference.accessibleOwnerType("schema property"),
            serializer = serializer,
            getProperty = reference::get,
            setProperty = (reference as? KMutableProperty1<R, T>)?.let { mutableReference ->
                { instance, value -> mutableReference.set(instance, value) }
            }
        ).also { it.configure(block) }

    final override fun <T> property(
        name: String,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: ((R, T) -> Unit)?,
        constructorParameterName: String?,
        block: SchemaPropertyBuilder<R, T>.() -> Unit
    ): SchemaProperty<R, T> =
        addProperty(
            name = name,
            sourceName = null,
            constructorParameterName = constructorParameterName,
            ownerType = null,
            serializer = serializer,
            getProperty = getProperty,
            setProperty = setProperty
        ).also { it.configure(block) }

    private fun <T> addProperty(
        name: String,
        sourceName: String?,
        constructorParameterName: String?,
        ownerType: KClass<*>?,
        serializer: Serializer<T>,
        getProperty: (R) -> T,
        setProperty: ((R, T) -> Unit)?
    ): SchemaProperty<R, T> {
        require(sourceName == null || sourceName.isNotBlank()) {
            "Schema property Kotlin names cannot be blank"
        }
        require(
            sourceName == null ||
                mutableProperties.none { it.sourceName == sourceName && it.ownerType == ownerType }
        ) {
            "Schema property '$sourceName' is already registered"
        }

        val property = SchemaProperty(
            getProperty = getProperty,
            setProperty = setProperty,
            serializer = serializer,
            name = name,
            sourceName = sourceName,
            constructorParameterName = constructorParameterName,
            ownerType = ownerType,
            constructorParameter = null
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
        for (property in properties) {
            require(property.ownerType == null || type.isSubclassOf(property.ownerType)) {
                "Property '${property.sourceName ?: property.name}' cannot be read from ${type.qualifiedName}"
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

    protected data class DecodedProperty(
        val value: Any?,
        val wasProvided: Boolean
    )

    protected fun decodeProperties(
        variant: Variant
    ): Map<SchemaProperty<T, *>, DecodedProperty> {
        val values = valuesForDeserialization(variant)
        val decoded = linkedMapOf<SchemaProperty<T, *>, DecodedProperty>()
        for (property in properties) {
            val encoded = values[property.name]
            if (encoded != null) {
                decoded[property] = DecodedProperty(
                    value = deserializeProperty(property, encoded),
                    wasProvided = true
                )
                continue
            }

            when (val presence = property.presence) {
                SchemaPropertyPresence.Required -> throw DataSerializationException(
                    "Missing required property '${property.name}' for ${type.qualifiedName}"
                )

                is SchemaPropertyPresence.Optional<*> -> if (presence.hasDefault) {
                    decoded[property] = DecodedProperty(
                        value = presence.defaultValue(),
                        wasProvided = false
                    )
                }
            }
        }
        return decoded
    }
}

internal class SchemaImpl<T : Any>(
    type: KClass<T>,
    id: Identifier,
    declaredProperties: List<SchemaProperty<T, *>>,
    untagged: Boolean,
    private val constructionFactory: (SchemaConstructionContext<T>.() -> T)?,
    allowMissingConstructor: Boolean
) : BaseSchema<T>(
    type,
    id,
    declaredProperties.map { it.bindConstructor(type) },
    untagged
) {
    private data class Binding<R : Any>(
        val property: SchemaProperty<R, *>,
        val parameter: KParameter?
    )

    private val constructor: KFunction<T>? =
        if (constructionFactory == null) type.primaryConstructor else null
    private val bindings: List<Binding<T>>

    init {
        require(constructionFactory != null || constructor != null || allowMissingConstructor) {
            "Schema type ${type.qualifiedName} has no primary constructor; configure constructs { ... }"
        }

        val concreteConstructor = constructor
        bindings = if (concreteConstructor == null) {
            emptyList()
        } else {
            concreteConstructor.isAccessible = true
            val parameters = concreteConstructor.parameters
                .filter { it.kind == KParameter.Kind.VALUE }
                .associateBy { it.name }
            val boundBindings = properties.map { property ->
                val parameter = property.constructorParameter
                require(parameter != null || property.writable) {
                    "Schema property '${property.name}' must bind to a constructor parameter or define a setter"
                }
                val optional = property.presence as? SchemaPropertyPresence.Optional<*>
                require(
                    parameter == null ||
                        parameter.isOptional ||
                        optional == null ||
                        optional.hasDefault
                ) {
                    "Optional schema property '${property.name}' has no default, but constructor parameter " +
                        "'${parameter?.name}' does not have a Kotlin default"
                }
                Binding(property, parameter)
            }

            val duplicateParameters = boundBindings
                .mapNotNull { it.parameter }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            require(duplicateParameters.isEmpty()) {
                "Schema for ${type.qualifiedName} binds multiple properties to constructor parameters: " +
                    duplicateParameters.joinToString { it.name ?: "<unnamed>" }
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
        val decoded = decodeProperties(variant)
        val instance = constructionFactory?.let { factory ->
            constructWithFactory(factory, decoded)
        } ?: constructWithPrimaryConstructor(decoded)
        DeserializeResult.Success(instance)
    } catch (exception: Exception) {
        DeserializeResult.Failure(exception)
    }

    private fun constructWithPrimaryConstructor(
        decoded: Map<SchemaProperty<T, *>, DecodedProperty>
    ): T {
        val concreteConstructor = constructor
            ?: throw DataSerializationException(
                "Schema type ${type.qualifiedName} cannot deserialize its base type because it has no constructor"
            )
        val arguments = linkedMapOf<KParameter, Any?>()
        for (binding in bindings) {
            val parameter = binding.parameter ?: continue
            decoded[binding.property]?.let { arguments[parameter] = it.value }
        }

        val instance = concreteConstructor.callBy(arguments)
        for (binding in bindings) {
            if (binding.parameter != null) continue
            val value = decoded[binding.property] ?: continue
            binding.property.setOn(instance, value.value)
        }
        return instance
    }

    private fun constructWithFactory(
        factory: SchemaConstructionContext<T>.() -> T,
        decoded: Map<SchemaProperty<T, *>, DecodedProperty>
    ): T {
        val context = ConstructionContext(decoded)
        val instance = factory(context)
        for ((property, value) in decoded) {
            if (property in context.consumedProperties) continue
            property.setOn(instance, value.value)
        }
        return instance
    }

    private inner class ConstructionContext(
        private val decoded: Map<SchemaProperty<T, *>, DecodedProperty>
    ) : SchemaConstructionContext<T> {
        val consumedProperties: MutableSet<SchemaProperty<T, *>> = linkedSetOf()

        override fun <V> value(property: KProperty1<*, V>): V =
            value(resolveProperty(property))

        override fun <V> value(property: SchemaProperty<*, V>): V {
            val resolved = resolveProperty(property)
            val decodedValue = decoded[resolved]
                ?: throw DataSerializationException(
                    "Optional property '${resolved.name}' was not provided and has no schema default"
                )
            consumedProperties += resolved
            @Suppress("UNCHECKED_CAST")
            return decodedValue.value as V
        }

        override fun wasProvided(property: KProperty1<*, *>): Boolean =
            decoded[resolvePropertyUntyped(property)]?.wasProvided == true

        override fun wasProvided(property: SchemaProperty<*, *>): Boolean =
            decoded[resolvePropertyUntyped(property)]?.wasProvided == true

        private fun <V> resolveProperty(property: KProperty1<*, V>): SchemaProperty<T, V> {
            val key = property.structuredPropertyKey("construction property")
            val resolved = properties.singleOrNull { it.structuredProperty.key == key }
                ?: throw DataSerializationException(
                    "Property ${key.ownerType.qualifiedName}.${key.propertyName} is not part of schema $id"
                )
            @Suppress("UNCHECKED_CAST")
            return resolved as SchemaProperty<T, V>
        }

        private fun <V> resolveProperty(property: SchemaProperty<*, V>): SchemaProperty<T, V> {
            val resolved = resolvePropertyUntyped(property)
            @Suppress("UNCHECKED_CAST")
            return resolved as SchemaProperty<T, V>
        }

        private fun resolvePropertyUntyped(
            property: KProperty1<*, *>
        ): SchemaProperty<T, *> {
            val key = property.structuredPropertyKey("construction property")
            return properties.singleOrNull { it.structuredProperty.key == key }
                ?: throw DataSerializationException(
                    "Property ${key.ownerType.qualifiedName}.${key.propertyName} is not part of schema $id"
                )
        }

        private fun resolvePropertyUntyped(
            property: SchemaProperty<*, *>
        ): SchemaProperty<T, *> =
            properties.singleOrNull { it.origin === property.origin }
                ?: throw DataSerializationException(
                    "Schema property '${property.name}' does not belong to schema $id"
                )
    }
}

internal class SingletonSchemaImpl<T : Any>(
    type: KClass<T>,
    id: Identifier,
    declaredProperties: List<SchemaProperty<T, *>>,
    untagged: Boolean
) : BaseSchema<T>(
    type,
    id,
    declaredProperties.map { it.bindConstructor(type) },
    untagged
) {
    private val instance: T
        get() = type.singletonObjectInstance()
            ?: error("Singleton schema $id requires ${type.qualifiedName} to be a Kotlin object")

    init {
        for (property in properties) {
            require(property.writable) {
                "Singleton schema property '${property.name}' must define a setter"
            }
        }
    }

    override fun validateSerializableValue(value: T) {
        require(value === instance) {
            "Singleton schema $id can only serialize its Kotlin object instance"
        }
    }

    override fun deserialize(variant: Variant): DeserializeResult<T> = try {
        val singleton = instance
        for ((property, decoded) in decodeProperties(variant)) {
            property.setOn(singleton, decoded.value)
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

internal fun <T : Any> SchemaBuilderImpl<T>.build(
    id: Identifier,
    allowMissingConstructor: Boolean = false
): Schema<T> {
    return buildSchema(id) { type, schemaId, schemaProperties, schemaUntagged ->
        SchemaImpl(
            type,
            schemaId,
            schemaProperties,
            schemaUntagged,
            constructionFactory,
            allowMissingConstructor
        )
    }
}

internal fun <T : Any> SchemaBuilderImpl<T>.buildSingleton(id: Identifier): Schema<T> {
    require(constructionFactory == null) {
        "Singleton schema $id uses its Kotlin object instance and cannot configure constructs { ... }"
    }
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
