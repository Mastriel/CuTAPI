package xyz.mastriel.cutapi.nbt.tags.notnull

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.nbt.tags.NBTTag
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

open class NotNullTag<T : Any>(
    override val key: String,
    override var compound: NBTCompound,
    val kClass: KClass<T>,
    override val default: T
) : NBTTag<T> {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!compound.hasKey(key)) try { store(default); println("Stored default!") } catch (_: Exception) {}
        return get()
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        store(value)
    }

    override fun store(value: T) {
        compound.setObject(key, value)
    }

    override fun get(): T {
        return compound.getObject(key, kClass.java) ?: default
    }
}