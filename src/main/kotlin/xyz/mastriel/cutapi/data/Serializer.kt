package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.utils.*
import kotlin.reflect.*

public sealed interface DataResult

public sealed interface SerializeResult : DataResult {
    public data class Success(public val value: Variant) : SerializeResult

    public data class Failure(public val error: Exception) : SerializeResult
}

public sealed interface DeserializeResult<out T> : DataResult {
    public data class Success<T>(public val value: T) : DeserializeResult<T>

    public data class Failure(public val error: Exception) : DeserializeResult<Nothing>
}

public fun SerializeResult.getOrThrow(): Variant = when (this) {
    is SerializeResult.Success -> value
    is SerializeResult.Failure -> throw error
}

public fun <T> DeserializeResult<T>.getOrThrow(): T = when (this) {
    is DeserializeResult.Success -> value
    is DeserializeResult.Failure -> throw error
}

public open class DataSerializationException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

public class VariantTypeException(
    public val expected: String,
    public val actual: Variant
) : DataSerializationException("Expected $expected, but found ${actual::class.simpleName}")

/**
 * Converts one application type to and from the storage-independent [Variant] tree.
 * Implementations may be written directly or produced with [schema].
 */
public interface Serializer<T> : EncodeOnlySerializer<T> {
    public fun deserialize(variant: Variant): DeserializeResult<T>

    public operator fun unaryPlus(): Serializable<T> = object : Serializable<T> {
        override val serializer: Serializer<T> = this@Serializer
    }
}

public interface TaggedSerializer<T> : Serializer<T>, Identifiable {
    public companion object : IdentifierRegistry<TaggedSerializer<*>>(id("cutapi:registry/tagged_serializer")) {
        public fun <T : Any> fromCompanion(type: KClass<T>): TaggedSerializer<T> {
            val instance = type.accessibleCompanionObjectInstance()
            require(instance is TaggedSerializer<*>) {
                "Companion object of ${type.qualifiedName} must implement ${TaggedSerializer::class.qualifiedName}"
            }
            @Suppress("UNCHECKED_CAST")
            return instance as TaggedSerializer<T>
        }

        public inline fun <reified T : Any> fromCompanion(): TaggedSerializer<T> = fromCompanion(T::class)
    }
}

/** Supplies the serializer used by an application value. */
public interface Serializable<T> {
    public val serializer: Serializer<T>
}

internal object EnumEntryCatalog {
    private val entriesByKey: MutableMap<String, SerializerValueDomain.Enum> =
        linkedMapOf()

    fun register(
        type: KClass<*>,
        values: List<String>
    ): SerializerValueDomain.Enum {
        val displayName = type.simpleName ?: "Enum"
        val key = type.qualifiedName ?: "$displayName-${values.hashCode()}"
        return synchronized(entriesByKey) {
            val existing = entriesByKey[key]
            if (existing != null) {
                require(existing.values == values) {
                    "Enum option key '$key' is already registered with different values"
                }
                existing
            } else {
                SerializerValueDomain.Enum(key, displayName, values)
                    .also { entriesByKey[key] = it }
            }
        }
    }

    fun get(key: String): SerializerValueDomain.Enum? =
        synchronized(entriesByKey) { entriesByKey[key] }

    fun keys(): Set<String> =
        synchronized(entriesByKey) { entriesByKey.keys.toSet() }
}

@PublishedApi
internal fun <E : Enum<E>> enumSerializer(
    type: KClass<E>,
    values: Array<E>
): Serializer<E> {
    val entries = EnumEntryCatalog.register(type, values.map { it.name })
    return serializer(
        descriptor = SerializerDescriptor.Primitive(
            kind = VariantKind.STRING,
            displayName = entries.displayName,
            valueDomain = entries
        ),
        serialize = { Variant.String(it.name) },
        deserialize = { variant ->
            val name = (variant as? Variant.String)?.value
                ?: throw VariantTypeException(entries.displayName, variant)
            values.firstOrNull { it.name == name }
                ?: throw VariantTypeException(entries.displayName, variant)
        }
    )
}

public interface VariantSerializer<T> : TaggedSerializer<T> {
    public companion object {
        internal val deferredRegistry = TaggedSerializer.defer()

        private fun <T> tagged(
            id: Identifier,
            descriptor: SerializerDescriptor,
            serialize: (T) -> Variant,
            deserialize: (Variant) -> T
        ): VariantSerializer<T> = object : VariantSerializer<T> {
            override val id: Identifier = id
            override val descriptor: SerializerDescriptor = descriptor

            override fun serialize(value: T): SerializeResult = try {
                SerializeResult.Success(serialize(value))
            } catch (exception: Exception) {
                SerializeResult.Failure(exception)
            }

            override fun deserialize(variant: Variant): DeserializeResult<T> = try {
                DeserializeResult.Success(deserialize(variant))
            } catch (exception: Exception) {
                DeserializeResult.Failure(exception)
            }
        }

        public fun <T> from(
            descriptor: SerializerDescriptor,
            serialize: (T) -> Variant,
            deserialize: (Variant) -> T
        ): Serializer<T> = serializer(descriptor, serialize, deserialize)

        public fun <T, R> mapped(
            serializer: Serializer<R>,
            serialize: (T) -> R,
            deserialize: (R) -> T
        ): Serializer<T> = serializer(
            descriptor = SerializerDescriptor.Mapped(serializer.descriptor),
            serialize = { serializer.serialize(serialize(it)).getOrThrow() },
            deserialize = { variant -> deserialize(serializer.deserialize(variant).getOrThrow()) }
        )

        public fun <T, R> mapped(
            serializer: Serializer<R>,
            id: Identifier,
            serialize: (T) -> R,
            deserialize: (R) -> T
        ): TaggedSerializer<T> = tagged(
            id = id,
            descriptor = SerializerDescriptor.Mapped(serializer.descriptor),
            serialize = { serializer.serialize(serialize(it)).getOrThrow() },
            deserialize = { variant -> deserialize(serializer.deserialize(variant).getOrThrow()) }
        )

        public inline fun <reified E : Enum<E>> Enum(): Serializer<E> =
            enumSerializer(E::class, enumValues<E>())

        public fun <T : Identifiable> Identifiable(registry: IdentifierRegistry<T>): Serializer<T> =
            serializer(
                descriptor = SerializerDescriptor.Primitive(
                    kind = VariantKind.IDENTIFIER,
                    valueDomain = SerializerValueDomain.Registry(registry.id)
                ),
                serialize = { Variant.Identifier(it.id) },
                deserialize = { variant ->
                    val identifier = Id.deserialize(variant).getOrThrow()
                    registry.get(identifier)
                }
            )

        public fun <T> ListOf(serializer: Serializer<T>): Serializer<List<T>> = serializer(
            descriptor = SerializerDescriptor.List(serializer.descriptor),
            serialize = { values -> Variant.List(values.map { serializer.serialize(it).getOrThrow() }) },
            deserialize = { variant ->
                val values = (variant as? Variant.List)?.value ?: throw VariantTypeException("List", variant)
                values.map { serializer.deserialize(it).getOrThrow() }
            }
        )

        public fun <T> ListOf(serializer: TaggedSerializer<T>): TaggedSerializer<List<T>> = tagged(
            id = id(serializer.id.namespace + ":list") / serializer.id.key,
            descriptor = SerializerDescriptor.List(serializer.descriptor),
            serialize = { values -> Variant.List(values.map { serializer.serialize(it).getOrThrow() }) },
            deserialize = { variant ->
                val values = (variant as? Variant.List)?.value ?: throw VariantTypeException("List", variant)
                values.map { serializer.deserialize(it).getOrThrow() }
            }
        )

        public fun <T : Resource> ResourceRef(): TaggedSerializer<ResourceRef<T>> = tagged(
            id = id(Plugin, "variant/resource_ref"),
            descriptor = SerializerDescriptor.Primitive(VariantKind.RESOURCE_REF),
            serialize = { Variant.ResourceRef(it) },
            deserialize = { variant ->
                ((variant as? Variant.ResourceRef)?.value ?: throw VariantTypeException("ResourceRef", variant)).cast()
            }
        )

        public val AnyVariant: VariantSerializer<Variant> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.ANY),
                serialize = { it },
                deserialize = { it }
            )
        }

        public val Null: VariantSerializer<Nothing?> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/null"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.NULL),
                serialize = { Variant.Null },
                deserialize = { variant ->
                    if (variant == Variant.Null) null else throw VariantTypeException(
                        "Null",
                        variant
                    )
                }
            )
        }

        public val String: VariantSerializer<String> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/string"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.STRING),
                serialize = { Variant.String(it) },
                deserialize = {
                    (it as? Variant.String)?.value ?: throw VariantTypeException("String", it)
                }
            )
        }

        public val Boolean: VariantSerializer<Boolean> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/boolean"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.BOOLEAN),
                serialize = { Variant.Boolean(it) },
                deserialize = {
                    (it as? Variant.Boolean)?.value ?: throw VariantTypeException("Boolean", it)
                }
            )
        }

        public val Byte: VariantSerializer<Byte> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/byte"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.BYTE),
                serialize = { Variant.Byte(it) },
                deserialize = {
                    (it as? Variant.Byte)?.value ?: throw VariantTypeException("Byte", it)
                }
            )
        }

        public val Short: VariantSerializer<Short> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/short"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.SHORT),
                serialize = { Variant.Short(it) },
                deserialize = {
                    (it as? Variant.Short)?.value ?: throw VariantTypeException("Short", it)
                }
            )
        }

        public val Int: VariantSerializer<Int> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/int"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.INT),
                serialize = { Variant.Int(it) },
                deserialize = {
                    (it as? Variant.Int)?.value ?: throw VariantTypeException("Int", it)
                }
            )
        }

        public val Long: VariantSerializer<Long> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/long"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.LONG),
                serialize = { Variant.Long(it) },
                deserialize = {
                    (it as? Variant.Long)?.value ?: throw VariantTypeException("Long", it)
                }
            )
        }

        public val Float: VariantSerializer<Float> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/float"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.FLOAT),
                serialize = { Variant.Float(it) },
                deserialize = {
                    (it as? Variant.Float)?.value ?: throw VariantTypeException("Float", it)
                }
            )
        }

        public val Double: VariantSerializer<Double> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/double"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.DOUBLE),
                serialize = { Variant.Double(it) },
                deserialize = {
                    (it as? Variant.Double)?.value ?: throw VariantTypeException("Double", it)
                }
            )
        }

        public val Char: VariantSerializer<Char> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/char"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.CHAR),
                serialize = { Variant.Char(it) },
                deserialize = {
                    (it as? Variant.Char)?.value ?: throw VariantTypeException("Char", it)
                }
            )
        }

        public val Id: VariantSerializer<Identifier> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/id"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.IDENTIFIER),
                serialize = { Variant.Identifier(it) },
                deserialize = {
                    (it as? Variant.Identifier)?.value ?: throw VariantTypeException("Identifier", it)
                }
            )
        }

        public val ResourceRef: VariantSerializer<ResourceRef<*>> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/resource_ref"),
                descriptor = SerializerDescriptor.Primitive(VariantKind.RESOURCE_REF),
                serialize = { Variant.ResourceRef(it) },
                deserialize = {
                    (it as? Variant.ResourceRef)?.value ?: throw VariantTypeException("ResourceRef", it)
                }
            )
        }

        public val List: VariantSerializer<List<Variant>> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/list"),
                descriptor = SerializerDescriptor.List(
                    SerializerDescriptor.Primitive(VariantKind.ANY)
                ),
                serialize = { Variant.List(it) },
                deserialize = {
                    (it as? Variant.List)?.value ?: throw VariantTypeException("List", it)
                }
            )
        }

        public val Map: VariantSerializer<Map<Variant, Variant>> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/map"),
                descriptor = SerializerDescriptor.Map(
                    key = SerializerDescriptor.Primitive(VariantKind.ANY),
                    value = SerializerDescriptor.Primitive(VariantKind.ANY)
                ),
                serialize = { Variant.Map(it) },
                deserialize = {
                    (it as? Variant.Map)?.value ?: throw VariantTypeException("Map", it)
                }
            )
        }
    }
}

public fun <T> serializer(
    descriptor: SerializerDescriptor,
    serialize: (T) -> Variant,
    deserialize: (Variant) -> T
): Serializer<T> = object : Serializer<T> {
    override val descriptor: SerializerDescriptor = descriptor

    override fun serialize(value: T): SerializeResult = try {
        SerializeResult.Success(serialize(value))
    } catch (exception: Exception) {
        SerializeResult.Failure(exception)
    }

    override fun deserialize(variant: Variant): DeserializeResult<T> = try {
        DeserializeResult.Success(deserialize(variant))
    } catch (exception: Exception) {
        DeserializeResult.Failure(exception)
    }
}

public fun <T> Serializer<T>.nullable(): Serializer<T?> = object : Serializer<T?> {
    override val descriptor: SerializerDescriptor =
        SerializerDescriptor.Nullable(this@nullable.descriptor)

    override fun serialize(value: T?): SerializeResult =
        if (value == null) SerializeResult.Success(Variant.Null) else this@nullable.serialize(value)

    override fun deserialize(variant: Variant): DeserializeResult<T?> =
        if (variant == Variant.Null) DeserializeResult.Success(null) else this@nullable.deserialize(variant)
}

public fun <T : Any> TaggedSerializer<T>.nullable(
    id: Identifier = this.id.appendSubId("nullable")
): TaggedSerializer<T?> = object : TaggedSerializer<T?> {
    override val id: Identifier = id
    override val descriptor: SerializerDescriptor =
        SerializerDescriptor.Nullable(this@nullable.descriptor)

    override fun serialize(value: T?): SerializeResult =
        if (value == null) SerializeResult.Success(Variant.Null) else this@nullable.serialize(value)

    override fun deserialize(variant: Variant): DeserializeResult<T?> =
        if (variant == Variant.Null) DeserializeResult.Success(null) else this@nullable.deserialize(variant)
}
