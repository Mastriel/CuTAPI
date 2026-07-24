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

    public val cbor: Cbor = CuTApiCbor

    override fun fromPrimitive(primitive: ByteArray): T {
        return cbor.decodeFromByteArray(serializer, primitive)
    }

    override fun toPrimitive(complex: T): ByteArray {
        return cbor.encodeToByteArray(serializer, complex)
    }


}

@OptIn(InternalSerializationApi::class)
public inline fun <reified T : Any> ObjectTagConverter(): ObjectTagConverter<T> {
    return ObjectTagConverter(T::class, T::class.serializer())
}

public inline fun <reified T : Any> ObjectTagConverter(serializer: KSerializer<T>): ObjectTagConverter<T> =
    ObjectTagConverter(T::class, serializer)
