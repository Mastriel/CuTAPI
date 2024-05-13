package xyz.mastriel.cutapi.utils.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

open class EnumSerializer<E : Enum<E>>(val kClass: KClass<E>) : KSerializer<E> {
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