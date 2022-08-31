package xyz.mastriel.cutapi.nbt.tags.nullable

import de.tr7zw.changeme.nbtapi.NBTCompound
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import xyz.mastriel.cutapi.CuTAPI
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class)
class NullableObjectTag<T : Any>(
    key: String,
    compound: NBTCompound,
    kClass: KClass<T>,
    default: T?,
    val serializer: KSerializer<T>
) : NullableTag<T>(key, compound, kClass, default) {

    val cbor = CuTAPI.cbor

    override fun get(): T? {
        if (isNull()) return null
        val bytes = compound.getByteArray(key)
        return cbor.decodeFromByteArray(serializer, bytes)
    }


    override fun store(value: T?) {
        if (value == null) return storeNull()
        val bytes = cbor.encodeToByteArray(serializer, value)
        compound.setByteArray(key, bytes)
    }

}