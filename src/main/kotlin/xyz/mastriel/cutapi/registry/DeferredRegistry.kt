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

public class DeferredDelegate<T : Identifiable> internal constructor(
    private val idRegistry: IdentifierRegistry<T>,
    private val deferredRegistry: DeferredRegistry<T>,
    private val producer: SingleProducer<T>
) : Deferred<T> {
    internal var id: Identifier? = null
    public override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return get();
    }

    public override fun get(): T {
        return producer.produce()
    }
}


public interface DeferredRegistry<T : Identifiable> {
    public val isOpen: Boolean

    /**
     * Registers a deferred item. Note that this must return an Identifiable with a consistent Identifier.
     */
    public fun register(producer: () -> T): Deferred<T>
    public fun getByProducer(producer: () -> T): Identifier
    public fun associateId(producer: () -> T, id: Identifier)
    public fun commitToRegistry()
}

public open class BasicDeferredRegistry<T : Identifiable> internal constructor(
    private val registry: IdentifierRegistry<T>,
    private val priority: RegistryPriority
) : DeferredRegistry<T> {
    protected data class DeferredItem<T : Identifiable>(
        val producer: SingleProducer<T>,
        val delegate: DeferredDelegate<T>
    )

    private val items: MutableList<DeferredItem<T>> = mutableListOf()
    private val producersToIds = mutableMapOf<SingleProducer<T>, Identifier>()
    final override var isOpen: Boolean = true
        protected set

    /**
     * Registers a deferred item. Note that this must return an Identifiable with a consistent Identifier.
     */
    override fun register(producer: () -> T): Deferred<T> {
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