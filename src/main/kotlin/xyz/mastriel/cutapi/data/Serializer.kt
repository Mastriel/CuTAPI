package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*
import kotlin.reflect.full.*

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
    expected: String,
    actual: Variant
) : DataSerializationException("Expected $expected, but found ${actual::class.simpleName}")

/**
 * Converts one application type to and from the storage-independent [Variant] tree.
 * Implementations may be written directly or produced with [schema].
 */
public interface Serializer<T> : Identifiable {
    public fun serialize(value: T): SerializeResult

    public fun deserialize(variant: Variant): DeserializeResult<T>

    public operator fun unaryPlus(): Serializable<T> = object : Serializable<T> {
        override val serializer: Serializer<T> = this@Serializer
    }

    public companion object : IdentifierRegistry<Serializer<*>>("Serializers") {
        public fun <T : Any> fromCompanion(type: KClass<T>): Serializer<T> {
            val instance = type.companionObjectInstance
            require(instance is Serializer<*>) {
                "Companion object of ${type.qualifiedName} must implement ${Serializer::class.qualifiedName}"
            }
            @Suppress("UNCHECKED_CAST")
            return instance as Serializer<T>
        }

        public inline fun <reified T : Any> fromCompanion(): Serializer<T> = fromCompanion(T::class)
    }
}

/** Supplies the serializer used by an application value. */
public interface Serializable<T> {
    public val serializer: Serializer<T>
}

public interface VariantSerializer<T> : Serializer<T> {
    public companion object {
        internal val deferredRegistry = Serializer.defer()

        public fun <T> from(
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

        public val AnyVariant: VariantSerializer<Variant> by deferredRegistry.register {
            from(
                id = id("cutapi:variant"),
                serialize = { it },
                deserialize = { it }
            )
        }

        public val Null: VariantSerializer<Nothing?> by deferredRegistry.register {
            from(
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
            from(
                id = id("cutapi:variant/string"),
                serialize = { Variant.String(it) },
                deserialize = {
                    (it as? Variant.String)?.value ?: throw VariantTypeException("String", it)
                }
            )
        }

        public val Boolean: VariantSerializer<Boolean> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/boolean"),
                serialize = { Variant.Boolean(it) },
                deserialize = {
                    (it as? Variant.Boolean)?.value ?: throw VariantTypeException("Boolean", it)
                }
            )
        }

        public val Byte: VariantSerializer<Byte> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/byte"),
                serialize = { Variant.Byte(it) },
                deserialize = {
                    (it as? Variant.Byte)?.value ?: throw VariantTypeException("Byte", it)
                }
            )
        }

        public val Short: VariantSerializer<Short> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/short"),
                serialize = { Variant.Short(it) },
                deserialize = {
                    (it as? Variant.Short)?.value ?: throw VariantTypeException("Short", it)
                }
            )
        }

        public val Int: VariantSerializer<Int> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/int"),
                serialize = { Variant.Int(it) },
                deserialize = {
                    (it as? Variant.Int)?.value ?: throw VariantTypeException("Int", it)
                }
            )
        }

        public val Long: VariantSerializer<Long> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/long"),
                serialize = { Variant.Long(it) },
                deserialize = {
                    (it as? Variant.Long)?.value ?: throw VariantTypeException("Long", it)
                }
            )
        }

        public val Float: VariantSerializer<Float> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/float"),
                serialize = { Variant.Float(it) },
                deserialize = {
                    (it as? Variant.Float)?.value ?: throw VariantTypeException("Float", it)
                }
            )
        }

        public val Double: VariantSerializer<Double> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/double"),
                serialize = { Variant.Double(it) },
                deserialize = {
                    (it as? Variant.Double)?.value ?: throw VariantTypeException("Double", it)
                }
            )
        }

        public val Char: VariantSerializer<Char> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/char"),
                serialize = { Variant.Char(it) },
                deserialize = {
                    (it as? Variant.Char)?.value ?: throw VariantTypeException("Char", it)
                }
            )
        }

        public val List: VariantSerializer<List<Variant>> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/list"),
                serialize = { Variant.List(it) },
                deserialize = {
                    (it as? Variant.List)?.value ?: throw VariantTypeException("List", it)
                }
            )
        }

        public val Map: VariantSerializer<Map<Variant, Variant>> by deferredRegistry.register {
            from(
                id = id("cutapi:variant/map"),
                serialize = { Variant.Map(it) },
                deserialize = {
                    (it as? Variant.Map)?.value ?: throw VariantTypeException("Map", it)
                }
            )
        }
    }
}

public fun <T : Any> Serializer<T>.nullable(
    id: Identifier = this.id.appendSubId("nullable")
): Serializer<T?> = object : Serializer<T?> {
    override val id: Identifier = id

    override fun serialize(value: T?): SerializeResult =
        if (value == null) SerializeResult.Success(Variant.Null) else this@nullable.serialize(value)

    override fun deserialize(variant: Variant): DeserializeResult<T?> =
        if (variant == Variant.Null) DeserializeResult.Success(null) else this@nullable.deserialize(variant)
}
