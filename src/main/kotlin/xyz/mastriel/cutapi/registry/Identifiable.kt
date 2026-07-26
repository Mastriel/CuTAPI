package xyz.mastriel.cutapi.registry

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.data.Serializable


/**
 * An interface that marks a class as identifiable using an [Identifier].
 */
public interface Identifiable {
    public val id: Identifier

    /**
     * Used to display this value in debugging tools.
     *
     * Serializable values use their existing serializer automatically. Override
     * this with [xyz.mastriel.cutapi.data.debugView] when debugging should expose
     * only part of the value or use a different representation.
     */
    public val debugView: DebugRepresentation<*>?
        get() = (this as? Serializable<*>)?.serializer
}

public sealed class SerialDefault<T : Identifiable?> {
    public class None<T : Identifiable?>() : SerialDefault<T>()
    public data class Some<T : Identifiable?>(val value: T) : SerialDefault<T>()
}


public open class IdentifiableSerializer<T : Identifiable>(
    public val serialName: String,
    public val map: IdentifierRegistry<T>,
    public val default: SerialDefault<T> = SerialDefault.None()
) : KSerializer<T> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): T {
        val identifier = IdentifierSerializer.deserialize(decoder)
        return when (val value = map.getOrNull(identifier)) {
            null -> when (default) {
                is SerialDefault.None -> throw SerializationException("Identifier $identifier not found in registry $serialName")
                is SerialDefault.Some -> default.value
            }

            else -> value
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        val identifier = value.id.toString()
        encoder.encodeString(identifier)
    }
}

public open class NullableIdentifiableSerializer<T : Identifiable>(
    public val serialName: String,
    public val map: IdentifierRegistry<T>,
    public val default: SerialDefault<T?> = SerialDefault.None()
) : KSerializer<T?> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): T? {
        val identifier = IdentifierSerializer.deserialize(decoder)

        if (identifier.toString() == "cutapi:null") {
            return when (default) {
                is SerialDefault.None -> null
                is SerialDefault.Some -> default.value
            }
        }

        return when (val value = map.getOrNull(identifier)) {
            null -> when (default) {
                is SerialDefault.None -> null
                is SerialDefault.Some -> default.value
            }

            else -> value
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) return encoder.encodeString("cutapi:null")
        val identifier = value.id.toString()
        encoder.encodeString(identifier)
    }
}
