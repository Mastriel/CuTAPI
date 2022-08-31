package xyz.mastriel.cutapi.nbt.tags.nullable

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTType
import xyz.mastriel.cutapi.nbt.tags.NBTTag
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


open class NullableTag<T : Any>(
    final override val key: String,
    final override var compound: NBTCompound,
    val kClass: KClass<T>,
    final override val default: T?
) : NBTTag<T?> {

    private var cachedValue : T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        if (!compound.hasKey(key)) try { store(default) } catch (_: Exception) {}
        if (isNull()) return null

        if (cachedValue != null) return cachedValue
        return get().also { cachedValue = it }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        if (value == null) return storeNull()
        cachedValue = value
        store(value)
    }

    override fun store(value: T?) {
        if (value == null) return storeNull()
        compound.setObject(key, value)
    }

    override fun get(): T? {
        if (isNull()) return null
        return compound.getObject(key, kClass.java)
    }

    protected fun storeNull() {
        compound.setString(key, NULL)
    }

    protected fun isNull(): Boolean {
        if (compound.getType(key) == NBTType.NBTTagString) {
            return compound.getString(key) == NULL
        }
        return false
    }


    companion object {
        private const val NULL = "\u0000NULL"
    }
}