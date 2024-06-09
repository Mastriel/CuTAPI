package xyz.mastriel.cutapi.pdc.tags.converters

import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import xyz.mastriel.cutapi.*
import kotlin.reflect.*

@OptIn(ExperimentalSerializationApi::class)
public class ObjectTagConverter<T : Any>(
    kClass: KClass<T>,
    public val serializer: KSerializer<T>
) : TagConverter<ByteArray, T>(ByteArray::class, kClass) {

    public val cbor: Cbor = CuTAPI.cbor

    override fun fromPrimitive(primitive: ByteArray): T {
        return cbor.decodeFromByteArray(serializer, primitive)
    }

    override fun toPrimitive(complex: T): ByteArray {
        return cbor.encodeToByteArray(serializer, complex)
    }


}