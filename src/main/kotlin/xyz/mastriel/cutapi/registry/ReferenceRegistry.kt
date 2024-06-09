package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.Plugin
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

// what the fuck is this
// -Mastriel (who also wrote this)
public open class ReferenceRegistry<T : Any> {
    protected val values: MutableMap<Identifier, KClass<out T>> = mutableMapOf<Identifier, KClass<out T>>()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     */
    public open fun register(item: KClass<out T>) {
        val identifiable = item.companionObjectInstance
        require(identifiable != null) { "${item.simpleName} must have a companion object that implements Identifiable." }
        require(identifiable is Identifiable) { "${item.simpleName}'s companion object does not implement Identifiable." }
        require(!values.containsKey(identifiable.id)) { "Two Identifiables cannot have the same ID in the same registry." }
        values[identifiable.id] = item
        Plugin.info("[REGISTRY] ${item.qualifiedName} added to a registry.")
    }

    /**
     * Get a [T] based on its corresponding [Identifier].
     *
     * @param id The [Identifier] associated with this [T]
     * @return The object
     * @throws IllegalStateException If this could not be found.
     */
    public fun get(id: Identifier): KClass<out T> {
        return getOrNull(id) ?: error("Identifier points to nothing.")
    }

    /**
     * Get a [T] based on its corresponding [Identifier], or null.
     *
     * @param id The [Identifier] associated with this [T]
     * @return The object, or null if it could not be found.
     */
    public fun getOrNull(id: Identifier): KClass<out T>? {
        return values[id]
    }

    /**
     * Get all the used IDs of this map.
     *
     * @return A set of the [Identifier]s.
     */
    public fun getAllIds(): Set<Identifier> {
        return values.keys
    }

    public fun isRegistered(kClass: KClass<out T>): Boolean {
        return values.values.contains(kClass)
    }
}