package xyz.mastriel.cutapi.registry

import kotlin.reflect.*


public interface Deferred<T : Identifiable> {
    public operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T

    public fun get(): T
}


public class DeferredDelegate<T : Identifiable> internal constructor(
    private val idRegistry: IdentifierRegistry<T>,
) : Deferred<T> {
    internal var id: Identifier? = null
    public override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return get();
    }

    public override fun get(): T {
        if (id == null) {
            error("Cannot get deferred item before it has been registered.")
        }
        return idRegistry.get(id!!)
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
    public fun commitRegistry()
}

public open class BasicDeferredRegistry<T : Identifiable> internal constructor(
    private val registry: IdentifierRegistry<T>,
    private val priority: RegistryPriority
) : DeferredRegistry<T> {
    protected data class DeferredItem<T : Identifiable>(
        val producer: () -> T,
        val delegate: DeferredDelegate<T>
    )

    private val items: MutableList<DeferredItem<T>> = mutableListOf()
    private val producersToIds = mutableMapOf<() -> T, Identifier>()
    final override var isOpen: Boolean = true
        protected set

    /**
     * Registers a deferred item. Note that this must return an Identifiable with a consistent Identifier.
     */
    override fun register(producer: () -> T): Deferred<T> {
        if (!isOpen) error("Deferred registry is already closed")
        return DeferredDelegate(registry).also {
            items += DeferredItem(producer, it)
        }
    }

    override fun getByProducer(producer: () -> T): Identifier {
        return producersToIds[producer] ?: error("Producer not registered")
    }

    override fun associateId(producer: () -> T, id: Identifier) {
        producersToIds[producer] = id
    }


    override fun commitRegistry() {
        if (!isOpen) error("Deferred registry is already closed")
        isOpen = false

        // Register all items in the registry
        registry.modifyRegistry(priority) {
            for ((producer, delegate) in items) {

                val item = producer()
                delegate.id = item.id
                register(item)
            }
        }
    }
}