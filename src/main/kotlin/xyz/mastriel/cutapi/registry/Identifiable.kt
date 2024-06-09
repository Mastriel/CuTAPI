package xyz.mastriel.cutapi.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


/**
 * An interface that marks a class as identifiable using an [Identifier].
 */
public interface Identifiable {
    public val id: Identifier
}


public open class IdentifiableSerializer<T: Identifiable>(public val serialName: String, public val map: IdentifierRegistry<T>) : KSerializer<T> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): T {
        val identifier = IdentifierSerializer.deserialize(decoder)
        return map.get(identifier)
    }

    override fun serialize(encoder: Encoder, value: T) {
        val identifier = value.id.toString()
        encoder.encodeString(identifier)
    }
}