package xyz.mastriel.cutapi.nbt.tags.notnull

import de.tr7zw.changeme.nbtapi.NBTCompound
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.nbt.tags.nullable.NullableTag
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class)
class NotNullObjectTag<T : Any>(
    key: String,
    compound: NBTCompound,
    kClass: KClass<T>,
    default: T,
    val serializer: KSerializer<T>
) : NotNullTag<T>(key, compound, kClass, default) {

    val cbor = CuTAPI.cbor

    override fun get(): T {
        val bytes = compound.getByteArray(key)
        return cbor.decodeFromByteArray(serializer, bytes)
    }


    override fun store(value: T) {
        val bytes = cbor.encodeToByteArray(serializer, value)
        compound.setByteArray(key, bytes)
    }

}