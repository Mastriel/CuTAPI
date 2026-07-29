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

internal sealed interface SerializerAvailableEntries {
    data class LiteralValues(
        val values: List<String>
    ) : SerializerAvailableEntries

    data class EnumValues(
        val key: String,
        val displayName: String,
        val values: List<String>
    ) : SerializerAvailableEntries

    data class RegistryValues(
        val registryId: Identifier
    ) : SerializerAvailableEntries
}

internal interface SerializerJsonMetadata {
    val expectedType: String
    val availableEntries: SerializerAvailableEntries?
}

internal object EnumEntryCatalog {
    private val entriesByKey: MutableMap<String, SerializerAvailableEntries.EnumValues> =
        linkedMapOf()

    fun register(
        type: KClass<*>,
        values: List<String>
    ): SerializerAvailableEntries.EnumValues {
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
                SerializerAvailableEntries.EnumValues(key, displayName, values)
                    .also { entriesByKey[key] = it }
            }
        }
    }

    fun get(key: String): SerializerAvailableEntries.EnumValues? =
        synchronized(entriesByKey) { entriesByKey[key] }

    fun keys(): Set<String> =
        synchronized(entriesByKey) { entriesByKey.keys.toSet() }
}

private fun <T> describedSerializer(
    serializer: Serializer<T>,
    expectedType: String,
    availableEntries: SerializerAvailableEntries? = null
): Serializer<T> = object : Serializer<T> by serializer, SerializerJsonMetadata {
    override val expectedType: String = expectedType
    override val availableEntries: SerializerAvailableEntries? = availableEntries
}

@PublishedApi
internal fun <E : Enum<E>> enumSerializer(
    type: KClass<E>,
    values: Array<E>
): Serializer<E> {
    val entries = EnumEntryCatalog.register(type, values.map { it.name })
    val delegate = serializer<E>(
        serialize = { Variant.String(it.name) },
        deserialize = { variant ->
            val name = (variant as? Variant.String)?.value
                ?: throw VariantTypeException(entries.displayName, variant)
            values.firstOrNull { it.name == name }
                ?: throw VariantTypeException(entries.displayName, variant)
        }
    )
    return describedSerializer(
        serializer = delegate,
        expectedType = entries.displayName,
        availableEntries = entries
    )
}

public interface VariantSerializer<T> : TaggedSerializer<T> {
    public companion object {
        internal val deferredRegistry = TaggedSerializer.defer()

        private fun <T> tagged(
            id: Identifier,
            serialize: (T) -> Variant,
            deserialize: (Variant) -> T
        ): VariantSerializer<T> = object : VariantSerializer<T> {
            override val id: Identifier = id

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
            serialize: (T) -> Variant,
            deserialize: (Variant) -> T
        ): Serializer<T> = serializer(serialize, deserialize)

        public fun <T, R> mapped(
            serializer: Serializer<R>,
            serialize: (T) -> R,
            deserialize: (R) -> T
        ): Serializer<T> = serializer(
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
            serialize = { serializer.serialize(serialize(it)).getOrThrow() },
            deserialize = { variant -> deserialize(serializer.deserialize(variant).getOrThrow()) }
        )

        public inline fun <reified E : Enum<E>> Enum(): Serializer<E> =
            enumSerializer(E::class, enumValues<E>())

        public fun <T : Identifiable> Identifiable(registry: IdentifierRegistry<T>): Serializer<T> =
            describedSerializer(
                serializer = mapped(
                    serializer = Id,
                    serialize = { it.id },
                    deserialize = { registry.get(it) }
                ),
                expectedType = "Identifier",
                availableEntries = SerializerAvailableEntries.RegistryValues(registry.id)
            )

        public fun <T> ListOf(serializer: Serializer<T>): Serializer<List<T>> = serializer(
            serialize = { values -> Variant.List(values.map { serializer.serialize(it).getOrThrow() }) },
            deserialize = { variant ->
                val values = (variant as? Variant.List)?.value ?: throw VariantTypeException("List", variant)
                values.map { serializer.deserialize(it).getOrThrow() }
            }
        )

        public fun <T> ListOf(serializer: TaggedSerializer<T>): TaggedSerializer<List<T>> = tagged(
            id = id(serializer.id.namespace + ":list") / serializer.id.key,
            serialize = { values -> Variant.List(values.map { serializer.serialize(it).getOrThrow() }) },
            deserialize = { variant ->
                val values = (variant as? Variant.List)?.value ?: throw VariantTypeException("List", variant)
                values.map { serializer.deserialize(it).getOrThrow() }
            }
        )

        public fun <T : Resource> ResourceRef(): TaggedSerializer<ResourceRef<T>> = tagged(
            id = id(Plugin, "variant/resource_ref"),
            serialize = { Variant.ResourceRef(it) },
            deserialize = { variant ->
                ((variant as? Variant.ResourceRef)?.value ?: throw VariantTypeException("ResourceRef", variant)).cast()
            }
        )

        public val AnyVariant: VariantSerializer<Variant> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant"),
                serialize = { it },
                deserialize = { it }
            )
        }

        public val Null: VariantSerializer<Nothing?> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/null"),
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
                serialize = { Variant.String(it) },
                deserialize = {
                    (it as? Variant.String)?.value ?: throw VariantTypeException("String", it)
                }
            )
        }

        public val Boolean: VariantSerializer<Boolean> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/boolean"),
                serialize = { Variant.Boolean(it) },
                deserialize = {
                    (it as? Variant.Boolean)?.value ?: throw VariantTypeException("Boolean", it)
                }
            )
        }

        public val Byte: VariantSerializer<Byte> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/byte"),
                serialize = { Variant.Byte(it) },
                deserialize = {
                    (it as? Variant.Byte)?.value ?: throw VariantTypeException("Byte", it)
                }
            )
        }

        public val Short: VariantSerializer<Short> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/short"),
                serialize = { Variant.Short(it) },
                deserialize = {
                    (it as? Variant.Short)?.value ?: throw VariantTypeException("Short", it)
                }
            )
        }

        public val Int: VariantSerializer<Int> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/int"),
                serialize = { Variant.Int(it) },
                deserialize = {
                    (it as? Variant.Int)?.value ?: throw VariantTypeException("Int", it)
                }
            )
        }

        public val Long: VariantSerializer<Long> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/long"),
                serialize = { Variant.Long(it) },
                deserialize = {
                    (it as? Variant.Long)?.value ?: throw VariantTypeException("Long", it)
                }
            )
        }

        public val Float: VariantSerializer<Float> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/float"),
                serialize = { Variant.Float(it) },
                deserialize = {
                    (it as? Variant.Float)?.value ?: throw VariantTypeException("Float", it)
                }
            )
        }

        public val Double: VariantSerializer<Double> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/double"),
                serialize = { Variant.Double(it) },
                deserialize = {
                    (it as? Variant.Double)?.value ?: throw VariantTypeException("Double", it)
                }
            )
        }

        public val Char: VariantSerializer<Char> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/char"),
                serialize = { Variant.Char(it) },
                deserialize = {
                    (it as? Variant.Char)?.value ?: throw VariantTypeException("Char", it)
                }
            )
        }

        public val Id: VariantSerializer<Identifier> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/id"),
                serialize = { Variant.Identifier(it) },
                deserialize = {
                    (it as? Variant.Identifier)?.value ?: throw VariantTypeException("Identifier", it)
                }
            )
        }

        public val ResourceRef: VariantSerializer<ResourceRef<*>> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/resource_ref"),
                serialize = { Variant.ResourceRef(it) },
                deserialize = {
                    (it as? Variant.ResourceRef)?.value ?: throw VariantTypeException("ResourceRef", it)
                }
            )
        }

        public val List: VariantSerializer<List<Variant>> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/list"),
                serialize = { Variant.List(it) },
                deserialize = {
                    (it as? Variant.List)?.value ?: throw VariantTypeException("List", it)
                }
            )
        }

        public val Map: VariantSerializer<Map<Variant, Variant>> by deferredRegistry.register {
            tagged(
                id = id("cutapi:variant/map"),
                serialize = { Variant.Map(it) },
                deserialize = {
                    (it as? Variant.Map)?.value ?: throw VariantTypeException("Map", it)
                }
            )
        }
    }
}

public fun <T> serializer(
    serialize: (T) -> Variant,
    deserialize: (Variant) -> T
): Serializer<T> = object : Serializer<T> {
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
    override fun serialize(value: T?): SerializeResult =
        if (value == null) SerializeResult.Success(Variant.Null) else this@nullable.serialize(value)

    override fun deserialize(variant: Variant): DeserializeResult<T?> =
        if (variant == Variant.Null) DeserializeResult.Success(null) else this@nullable.deserialize(variant)
}

public fun <T : Any> TaggedSerializer<T>.nullable(
    id: Identifier = this.id.appendSubId("nullable")
): TaggedSerializer<T?> = object : TaggedSerializer<T?> {
    override val id: Identifier = id

    override fun serialize(value: T?): SerializeResult =
        if (value == null) SerializeResult.Success(Variant.Null) else this@nullable.serialize(value)

    override fun deserialize(variant: Variant): DeserializeResult<T?> =
        if (variant == Variant.Null) DeserializeResult.Success(null) else this@nullable.deserialize(variant)
}
