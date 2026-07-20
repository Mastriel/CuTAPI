package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.full.*

public sealed interface DataResult

public sealed interface SerializeResult : DataResult {
    public data class Success(val value: Variant) : SerializeResult {}
    public data class GeneralFailure(val error: Exception) : SerializeResult {}
}

public sealed interface DeserializeResult<T> : DataResult {
    public data class Success<T>(val value: T) : DeserializeResult<T> {}
    public data class GeneralFailure<T>(val error: Exception) : DeserializeResult<T> {}
}

public interface Serializer<T> : Identifiable {
    public fun serialize(value: T): SerializeResult

    public fun deserialize(variant: Variant): DeserializeResult<T>

    public operator fun unaryPlus(): Serializable<T> {
        return object : Serializable<T> {
            override val serializer: Serializer<T> = this@Serializer
        }
    }

    public companion object : IdentifierRegistry<Serializer<*>>("Serializers") {
        public inline fun <reified T> unsafeFrom(): Serializer<T> {
            val instance = T::class.companionObjectInstance;
            if (instance is Serializer<*>) {
                @Suppress("UNCHECKED_CAST")
                return instance as Serializer<T>
            } else {
                error("Companion object of ${T::class.qualifiedName} must implement ${Serializer::class.qualifiedName} to use unsafeFrom<T>().")
            }
        }
    }
}


public interface Serializable<T> {
    public val serializer: Serializer<T>
}

public interface PrimitiveSerializer<T> : Serializer<T> {

    public companion object {
        internal fun <T : Any> from(
            id: Identifier,
            serialize: (T) -> Variant,
            deserialize: (Variant) -> T
        ): PrimitiveSerializer<T> {
            return object : PrimitiveSerializer<T> {
                override fun serialize(value: T): SerializeResult {
                    try {
                        return SerializeResult.Success(serialize(value))
                    } catch (ex: Exception) {
                        return SerializeResult.GeneralFailure(ex)
                    }
                }

                override fun deserialize(variant: Variant): DeserializeResult<T> {
                    TODO("Not yet implemented")
                }

                override val id: Identifier = id

            }
        }

        public val String: PrimitiveSerializer<String> = from(
            id = id(Plugin, "primitive/string"),
            serialize = { Variant.String(it) },
            deserialize = { it.value as String }
        )

        public val Int: PrimitiveSerializer<Int> = from(
            id = id(Plugin, "primitive/int"),
            serialize = { Variant.Int(it) },
            deserialize = { it.value as Int }
        )
    }
}