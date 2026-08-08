package xyz.mastriel.cutapi.pdc.tags

import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

public open class NotNullTag<P : Any, C : Any>(
    override val key: Identifier,
    override var container: TagContainer,
    override val defaultProducer: () -> C,
    private val converter: TagConverter<P, C>
) : Tag<C> {

    private var cachedValue: C? = null
    private var hasCachedValue: Boolean = false

    override fun store(value: C) {
        container.set(key, value, converter)

        cachedValue = value
        hasCachedValue = true
    }

    @Suppress("DuplicatedCode")
    override fun get(): C {
        if (hasCachedValue) return cachedValue!!

        val value = if (container.has(key)) {
            container.get(key, converter)!!
        } else {
            defaultProducer()
        }

        cachedValue = value
        hasCachedValue = true
        return value
    }

    public operator fun getValue(thisRef: Any?, property: KProperty<*>): C {
        return get()
    }

    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: C) {
        store(value)
    }

}
