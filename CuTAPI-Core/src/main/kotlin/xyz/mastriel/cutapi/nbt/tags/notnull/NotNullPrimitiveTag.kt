package xyz.mastriel.cutapi.nbt.tags.notnull

import de.tr7zw.changeme.nbtapi.NBTCompound
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import kotlin.reflect.KClass

class NotNullPrimitiveTag<T : Any>(
    key: String,
    compound: NBTCompound,
    kClass: KClass<T>,
    default: T,
    val getter: NBTCompound.(key: String) -> T,
    val setter: NBTCompound.(key: String, value: T) -> Unit
) : NotNullTag<T>(key, compound, kClass, default) {

    override fun get(): T {
        return getter(compound, key)
    }

    override fun store(value: T) {
        setter(compound, key, value)
    }
}