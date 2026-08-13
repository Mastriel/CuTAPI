package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*
import kotlin.reflect.full.*

public interface PolySchemaBuilder<R : Any> : SchemaBuilder<R> {
    /** Includes an already-created subtype schema. */
    public fun <S : R> include(schema: Schema<S>)
}

public interface PolySchema<T : Any> : Schema<T> {
    public val includedTypes: Set<KClass<out T>>

    override val descriptor: SerializerDescriptor<SerializerShape.Polymorphic>

    /** Includes an already-created subtype schema after this polymorphic schema has been built. */
    public fun <S : T> include(schema: Schema<S>)
}

private class PolySchemaBuilderImpl<R : Any>(
    private val schemaBuilder: SchemaBuilderImpl<R>
) : PolySchemaBuilder<R>, SchemaBuilder<R> by schemaBuilder {
    private val mutableIncludes: MutableList<Schema<out R>> = mutableListOf()

    val includes: List<Schema<out R>> get() = mutableIncludes.toList()

    override fun <S : R> include(schema: Schema<S>) {
        add(schema)
    }

    private fun add(schema: Schema<out R>) {
        require(mutableIncludes.none { it.type == schema.type }) {
            "Schema for subtype ${schema.type.qualifiedName} is already included"
        }
        mutableIncludes += schema
    }
}

private class PolySchemaImpl<T : Any>(
    private val base: Schema<T>,
    initialIncludes: List<Schema<out T>>
) : PolySchema<T>, Schema<T> by base {
    private val includes: MutableList<Schema<out T>> = mutableListOf()

    init {
        initialIncludes.forEach(::add)
    }

    override val includedTypes: Set<KClass<out T>>
        get() = includes.mapTo(linkedSetOf()) { it.type }

    override val descriptor: SerializerDescriptor<SerializerShape.Polymorphic>
        get() = SerializerDescriptor.polymorphic(
            base = base.descriptor.requireObjectDescriptor(base.id),
            included = includes.map { schema ->
                when (val shape = schema.descriptor.shape) {
                    is SerializerShape.Object -> schema.descriptor.requireObjectDescriptor(schema.id)
                    is SerializerShape.Polymorphic -> shape.base
                }
            }
        )

    override fun <S : T> include(schema: Schema<S>) {
        add(schema)
    }

    override fun serialize(value: T): SerializeResult = try {
        val runtimeType = value::class
        val resolved = resolveIncludes()
        val matches = resolved.filter { it.type.isInstance(value) }
        val selected = when {
            matches.isEmpty() -> null
            matches.size == 1 -> matches.single()
            else -> matches.singleOrNull { candidate ->
                matches.all { other -> candidate === other || candidate.type.isSubclassOf(other.type) }
            } ?: throw DataSerializationException(
                "Runtime type ${runtimeType.qualifiedName} matches multiple included schemas"
            )
        }

        if (selected != null) {
            @Suppress("UNCHECKED_CAST")
            return (selected as Schema<T>).serialize(value)
        }
        require(runtimeType == type) {
            "No schema included in $id can serialize subtype ${runtimeType.qualifiedName}"
        }
        base.serialize(value)
    } catch (exception: Exception) {
        SerializeResult.Failure(exception)
    }

    override fun deserialize(variant: Variant): DeserializeResult<T> = try {
        val values = variant.stringValues()
        val serializedId = (values[SCHEMA_TYPE_DISCRIMINATOR] as? Variant.String)?.value

        val resolved = resolveIncludes()
        if (serializedId == id.toString() || serializedId == null && untagged) {
            return base.deserialize(variant)
        }
        require(serializedId != null) {
            "Polymorphic schema $id requires string property '$SCHEMA_TYPE_DISCRIMINATOR'. Is your schema untagged?"
        }

        val exact = resolved.singleOrNull { it.id.toString() == serializedId }
        if (exact != null) {
            @Suppress("UNCHECKED_CAST")
            return (exact as Schema<T>).deserialize(variant)
        }

        val successful = resolved.mapNotNull { schema ->
            when (val result = schema.deserialize(variant)) {
                is DeserializeResult.Success -> result
                is DeserializeResult.Failure -> null
            }
        }
        require(successful.size == 1) {
            if (successful.isEmpty()) {
                "Polymorphic schema $id does not include serialized type '$serializedId'"
            } else {
                "Serialized type '$serializedId' matched multiple schemas included by $id"
            }
        }
        successful.single()
    } catch (exception: Exception) {
        DeserializeResult.Failure(exception)
    }

    private fun add(schema: Schema<out T>) {
        require(schema.type.isSubclassOf(base.type)) {
            "Included schema type ${schema.type.qualifiedName} must inherit ${base.type.qualifiedName}"
        }
        require(includes.none { it.type == schema.type }) {
            "Schema for subtype ${schema.type.qualifiedName} is already included"
        }
        require(schema.id != id) {
            "Polymorphic schema $id cannot include a subtype schema with the same identifier"
        }
        require(includes.none { it.id == schema.id }) {
            "Polymorphic schema $id already includes schema identifier ${schema.id}"
        }
        includes += schema
    }

    private fun resolveIncludes(): List<Schema<out T>> {
        for (schema in includes) {
            require(!schema.untagged) {
                "Subtype schema ${schema.id} cannot be untagged when included by polymorphic schema $id"
            }
        }
        require(includes.map { it.id }.distinct().size == includes.size) {
            "Polymorphic schema $id includes duplicate schema identifiers"
        }
        require(includes.none { it.id == id }) {
            "Polymorphic schema $id includes a subtype schema with the same identifier"
        }
        return includes
    }
}

private class DeferredPolySchema<T : Any>(
    override val id: Identifier,
    private val knownType: KClass<T>? = null,
    resolve: () -> PolySchema<T>
) : PolySchema<T> {
    private val resolved: PolySchema<T> by lazy(LazyThreadSafetyMode.SYNCHRONIZED, resolve)

    override val type: KClass<T> get() = knownType ?: resolved.type
    override val properties: List<SchemaProperty<T, *>> get() = resolved.properties
    override val untagged: Boolean get() = resolved.untagged
    override val strict: Boolean get() = resolved.strict
    override val includedTypes: Set<KClass<out T>> get() = resolved.includedTypes
    override val descriptor: SerializerDescriptor<SerializerShape.Polymorphic> get() = resolved.descriptor

    override fun serialize(value: T): SerializeResult = resolved.serialize(value)

    override fun deserialize(variant: Variant): DeserializeResult<T> = resolved.deserialize(variant)

    override fun <S : T> include(schema: Schema<S>) {
        resolved.include(schema)
    }
}

@Suppress("UNCHECKED_CAST")
private fun SerializerDescriptor<*>.requireObjectDescriptor(
    schemaId: Identifier
): SerializerDescriptor<SerializerShape.Object> {
    require(shape is SerializerShape.Object) {
        "Schema $schemaId must have an object shape"
    }
    return this as SerializerDescriptor<SerializerShape.Object>
}

/**
 * Creates an unregistered polymorphic schema.
 *
 * Register the returned schema with [Schema.modifyRegistry] during plugin startup.
 */
public fun <T : Any> polySchema(
    id: Identifier,
    block: PolySchemaBuilder<T>.() -> Unit
): PolySchema<T> = DeferredPolySchema(id) {
    val schemaBuilder = SchemaBuilderImpl<T>()
    val builder = PolySchemaBuilderImpl(schemaBuilder).apply(block)
    PolySchemaImpl(schemaBuilder.build(id, allowMissingConstructor = true), builder.includes)
}

public fun <T : Any> polySchema(
    type: KClass<T>,
    id: Identifier,
    block: PolySchemaBuilder<T>.() -> Unit
): PolySchema<T> = DeferredPolySchema(id, type) {
    val schemaBuilder = SchemaBuilderImpl(type)
    val builder = PolySchemaBuilderImpl(schemaBuilder).apply(block)
    PolySchemaImpl(schemaBuilder.build(id, allowMissingConstructor = true), builder.includes)
}
