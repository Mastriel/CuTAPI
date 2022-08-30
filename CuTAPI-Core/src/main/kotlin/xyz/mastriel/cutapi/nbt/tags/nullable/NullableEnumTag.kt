package xyz.mastriel.cutapi.nbt.tags.nullable

import de.tr7zw.changeme.nbtapi.NBTCompound
import org.bukkit.OfflinePlayer
import kotlin.reflect.KClass

class NullableEnumTag<T : Enum<T>>(key: String, compound: NBTCompound, default: T?, kClass: KClass<T>) :
    NullableTag<T>(key, compound, kClass, default) {

    init {
        if (!compound.hasKey(key)) try { store(default) } catch (_: Exception) {}
    }

    override fun get(): T? {
        if (isNull()) return null
        val enum = compound.getString(key)
        return kClass.java.enumConstants
            .first { it.name == enum }
    }

    override fun store(value: T?) {
        if (value == null) return storeNull()
        compound.setString(key, value.name)
    }
}
