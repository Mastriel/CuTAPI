package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTContainer
import de.tr7zw.changeme.nbtapi.NBTType
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


open class NullableTag<T: Any>(val key: String, val nbtContainer: NBTContainer, val kClass: KClass<T>, default: T?)
    : NBTTag<T?> {

    init {
        @Suppress("LeakingThis")
        if (!nbtContainer.hasKey(key)) store(default)
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        if (isNull()) return null
        return get()
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        if (value == null) return storeNull()
        store(value)
    }

    override fun store(value: T?) {
        nbtContainer.setObject(key, value)
    }

    override fun get() : T? {
        return nbtContainer.getObject(key, kClass.java)
    }

    protected fun storeNull() {
        nbtContainer.setString(key, NULL)
    }

    protected fun isNull() : Boolean {
        if (nbtContainer.getType(key) == NBTType.NBTTagString) {
            return nbtContainer.getString(key) == NULL
        }
        return false
    }


    companion object {
        private const val NULL = "\u0000NULL"
    }
}