package xyz.mastriel.cutapi.utils.serializers

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.*

public open class EnumSerializer<E : Enum<E>>(public val kClass: KClass<E>) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("cutapi:enum", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): E {
        return decoder.decodeString().let { value ->
            kClass.java.enumConstants.first { value == it.name }
        }
    }

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(value.name)
    }
}