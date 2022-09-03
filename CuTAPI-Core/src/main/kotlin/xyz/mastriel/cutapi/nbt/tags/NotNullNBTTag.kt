package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.nbt.tags.converters.TagConverter
import kotlin.reflect.KProperty

class NotNullNBTTag<P: Any, C : Any>(
    override val key: String,
    override var compound: NBTCompound,
    override val default: C,
    private val converter: TagConverter<P, C>
) : Tag<C> {

    private var cachedValue : C? = null

    override fun store(value: C) {
        val primitive = converter.toPrimitive(value)

        Tag.setPrimitiveValue(converter.primitiveClass, compound, key, primitive)

        cachedValue = value
    }

    @Suppress("DuplicatedCode")
    override fun get(): C {
        if (cachedValue != null) return cachedValue!!
        if (!compound.hasKey(key)) return default

        val primitive = Tag.getPrimitiveValue(converter.primitiveClass, compound, key)

        val value = converter.fromPrimitive(primitive!!)

        cachedValue = value
        return value
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): C {
        return get()
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: C) {
        store(value)
    }

}