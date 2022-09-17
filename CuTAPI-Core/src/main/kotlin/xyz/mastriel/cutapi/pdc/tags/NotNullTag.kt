package xyz.mastriel.cutapi.pdc.tags

import xyz.mastriel.cutapi.pdc.tags.converters.TagConverter
import kotlin.reflect.KProperty

open class NotNullTag<P: Any, C : Any>(
    override val key: String,
    override var container: TagContainer,
    override val default: C,
    private val converter: TagConverter<P, C>
) : Tag<C> {

    private var cachedValue : C? = null

    override fun store(value: C) {
        container.set(key, value, converter)

        cachedValue = value
    }

    @Suppress("DuplicatedCode")
    override fun get(): C {
        if (cachedValue != null) return cachedValue!!
        if (!container.has(key)) return default

        val value = container.get(key, converter)!!

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