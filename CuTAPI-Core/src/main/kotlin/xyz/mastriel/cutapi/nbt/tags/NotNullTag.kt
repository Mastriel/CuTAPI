package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTContainer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

open class NotNullTag<T: Any>(val key: String, val nbtContainer: NBTContainer, val kclass: KClass<T>, val default: T)
    : NBTTag<T> {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return get()
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        store(value)
    }

    override fun store(value: T) {
        nbtContainer.setObject(key, value)
    }

    override fun get() : T {
        return nbtContainer.getObject(key, kclass.java) ?: default
    }
}