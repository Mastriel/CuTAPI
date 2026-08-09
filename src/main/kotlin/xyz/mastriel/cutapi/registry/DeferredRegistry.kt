package xyz.mastriel.cutapi.registry

import kotlin.reflect.*


public interface Deferred<T : Identifiable> {
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T

    public fun get(): T

    public fun hasEarlyInit(): Boolean = false
}


public class SingleProducer<T>(private val producer: () -> T) {
    private var value: T? = null
    public fun produce(): T {
        if (value == null) value = producer()
        return value!!;
    }
}

public class DeferredDelegate<T : Identifiable, V : T> internal constructor(
    private val idRegistry: IdentifierRegistry<T>,
    private val deferredRegistry: DeferredRegistry<T>,
    private val producer: SingleProducer<V>
) : Deferred<V> {
    internal var id: Identifier? = null
    public override operator fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return get();
    }

    public override fun get(): V {
        return producer.produce()
    }
}


public interface DeferredRegistry<T : Identifiable> {
    public val isOpen: Boolean

    /**
     * Registers a deferred item. Note that this must return an Identifiable with a consistent Identifier.
     */
    public fun <V : T> register(producer: () -> V): Deferred<V>
    public fun getByProducer(producer: () -> T): Identifier
    public fun associateId(producer: () -> T, id: Identifier)
    public fun commitToRegistry()
}

public open class BasicDeferredRegistry<T : Identifiable> internal constructor(
    private val registry: IdentifierRegistry<T>,
    private val priority: RegistryPriority
) : DeferredRegistry<T> {
    protected data class DeferredItem<T : Identifiable, V : T>(
        val producer: SingleProducer<V>,
        val delegate: DeferredDelegate<T, V>
    )

    private val items: MutableList<DeferredItem<T, *>> = mutableListOf()
    private val producersToIds = mutableMapOf<SingleProducer<T>, Identifier>()
    final override var isOpen: Boolean = true
        protected set

    /**
     * Registers a deferred item. Note that this must return an Identifiable with a consistent Identifier.
     */
    override fun <V : T> register(producer: () -> V): Deferred<V> {
        if (!isOpen) error("Deferred registry is already closed")
        val single = SingleProducer(producer)
        return DeferredDelegate(registry, this, single).also {
            items += DeferredItem(single, it)
        }
    }

    override fun getByProducer(producer: () -> T): Identifier {
        val single = SingleProducer(producer)
        return producersToIds[single] ?: error("Producer not registered")
    }

    override fun associateId(producer: () -> T, id: Identifier) {
        val single = SingleProducer(producer)
        producersToIds[single] = id
    }


    override fun commitToRegistry() {
        if (!isOpen) error("Deferred registry is already closed")
        check(registry.isOpen) { "Registry '${registry.id}' is already closed" }
        isOpen = false

        // Register all items in the registry
        registry.modifyRegistry(priority) {
            for ((producer, delegate) in items) {

                val item = producer.produce()
                delegate.id = item.id
                register(item)
            }
        }
    }
}
