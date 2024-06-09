package xyz.mastriel.cutapi.pdc.tags

import xyz.mastriel.cutapi.pdc.tags.converters.*
import kotlin.reflect.*

public open class NullableTag<P : Any, C : Any>(
    override val key: String,
    override var container: TagContainer,
    override val default: C?,
    private val converter: TagConverter<P, C>
) : Tag<C?> {

    private var cachedValue : C? = null

    override fun store(value: C?) {
        if (value == null) return container.storeNull(key)
        container.set(key, value, converter)

        cachedValue = value
    }

    @Suppress("DuplicatedCode")
    override fun get(): C? {
        if (container.isNull(key)) return null
        if (cachedValue != null) return cachedValue!!

        val value = container.get(key, converter)

        cachedValue = value
        return value
    }

    public operator fun getValue(thisRef: Any?, property: KProperty<*>): C? {
        return get()
    }

    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: C?) {
        store(value)
    }


}