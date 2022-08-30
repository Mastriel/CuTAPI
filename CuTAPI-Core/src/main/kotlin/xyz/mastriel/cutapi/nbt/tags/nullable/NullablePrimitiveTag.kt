package xyz.mastriel.cutapi.nbt.tags.nullable

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.nbt.tags.notnull.NotNullTag
import kotlin.reflect.KClass

class NullablePrimitiveTag<T : Any>(
    key: String,
    compound: NBTCompound,
    kClass: KClass<T>,
    default: T?,
    val getter: NBTCompound.(key: String) -> T?,
    val setter: NBTCompound.(key: String, value: T) -> Unit
) :
    NullableTag<T>(key, compound, kClass, default) {

    override fun get(): T? {
        if (isNull()) return null
        return compound.getter(key)
    }

    override fun store(value: T?) {
        if (value == null) return storeNull()
        return compound.setter(key, value)

    }
}