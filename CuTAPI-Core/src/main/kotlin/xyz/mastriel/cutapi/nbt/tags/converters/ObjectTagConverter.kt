package xyz.mastriel.cutapi.nbt.tags.converters

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import xyz.mastriel.cutapi.CuTAPI
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class)
class ObjectTagConverter<T : Any>(
    kClass: KClass<T>,
    val serializer: KSerializer<T>
) : TagConverter<ByteArray, T>(ByteArray::class, kClass) {

    val cbor = CuTAPI.cbor

    override fun fromPrimitive(primitive: ByteArray): T {
        return cbor.decodeFromByteArray(serializer, primitive)
    }

    override fun toPrimitive(complex: T): ByteArray {
        return cbor.encodeToByteArray(serializer, complex)
    }


}