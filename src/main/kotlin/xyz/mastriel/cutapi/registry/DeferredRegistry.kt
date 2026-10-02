package xyz.mastriel.cutapi.registry

import java.util.*
import kotlin.reflect.*


public interface Deferred<T : Identifiable> {
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T

    public fun get(): T

    public fun hasEarlyInit(): Boolean = false
}


public class SingleProducer<T>(private val producer: () -> T) {
    private var value: Any? = null

    @Volatile
    public var hasProduced: Boolean = false
        private set

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    public fun produce(): T {
        if (!hasProduced) {
            value = producer()
            hasProduced = true
        }
        return value as T
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

    public override fun hasEarlyInit(): Boolean = producer.hasProduced
}


public interface DeferredRegistry<T : Identifiable> {
    public val isOpen: Boolean

    /**
     * Registers a deferred item. Note that this must return an Identifiable with a consistent Identifier.
     */
    public fun <V : T> register(producer: () -> V): Deferred<V>
    public fun getByProducer(producer: () -> T): Identifier
    public fun associateId(producer: () -> T, id: Identifier)

    /**
     * Resolves duplicates in the deferred registry to allowing the last item to take priority, instead of throwing an error.
     */
    public var overrideDuplicates: Boolean
    public fun commitToRegistry()
}

public open class BasicDeferredRegistry<T : Identifiable> internal constructor(
    protected val registry: IdentifierRegistry<T>,
    protected val priority: RegistryPriority
) : DeferredRegistry<T> {
    protected data class DeferredItem<T : Identifiable, V : T>(
        val producer: SingleProducer<V>,
        val delegate: DeferredDelegate<T, V>,
        var declaredId: Identifier? = null,
    )

    protected val items: MutableList<DeferredItem<T, *>> = mutableListOf()
    private val producersToItems: IdentityHashMap<Any, DeferredItem<T, *>> = IdentityHashMap()
    final override var isOpen: Boolean = true
        protected set

    /**
     * Resolves duplicates in the deferred registry to allowing the last item to take priority, instead of throwing an error.
     */
    public override var overrideDuplicates: Boolean = true

    /**
     * Registers a deferred item. Note that this must return an Identifiable with a consistent Identifier.
     */
    override fun <V : T> register(producer: () -> V): Deferred<V> {
        if (!isOpen) error("Deferred registry is already closed")
        val single = SingleProducer(producer)
        return DeferredDelegate(registry, this, single).also {
            val item = DeferredItem(single, it)
            items += item
            producersToItems[producer] = item
        }
    }

    override fun getByProducer(producer: () -> T): Identifier {
        val item = producersToItems[producer] ?: error("Producer not registered")
        return item.declaredId ?: item.producer.produce().id
    }

    override fun associateId(producer: () -> T, id: Identifier) {
        check(isOpen) { "Deferred registry is already closed" }
        val item = producersToItems[producer] ?: error("Producer not registered")
        check(item.declaredId == null || item.declaredId == id) {
            "Producer is already associated with ${item.declaredId}; cannot associate it with $id."
        }
        item.declaredId = id
    }


    override fun commitToRegistry() {
        requireCanCommit()
        isOpen = false

        // Register all items in the registry
        registry.modifyRegistry(priority) {
            val createdItems = mutableListOf<T>()
            for (entry in items) {
                val item = entry.producer.produce()
                entry.delegate.id = item.id
                createdItems.add(item)
            }

            if (overrideDuplicates) {
                createdItems
                    .reversed()
                    .distinctBy { it.id }
                    .reversed()
                    .forEach { item -> register(item) }
            } else {
                createdItems
                    .forEach { item -> register(item) }
            }

        }
    }

    protected fun requireCanCommit() {
        check(isOpen) { "Deferred registry is already closed" }
        check(registry.isOpen) { "Registry '${registry.id}' is already closed" }
    }
}
