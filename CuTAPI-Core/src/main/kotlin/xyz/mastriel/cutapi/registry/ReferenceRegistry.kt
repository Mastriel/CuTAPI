package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.Plugin
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

open class ReferenceRegistry <T: Any> {
    protected val values = mutableMapOf<Identifier, KClass<T>>()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     */
    open fun register(item: KClass<T>) {
        val identifiable = item.companionObjectInstance
        require(identifiable != null) { "${item.simpleName} must have a companion object that implements Identifiable." }
        require(identifiable !is Identifiable) { "${item.simpleName}'s companion object does not implement Identifiable." }
        identifiable as Identifiable
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
    fun get(id: Identifier) : KClass<T> {
        return getOrNull(id) ?: error("Identifier points to no available identifiable object.")
    }

    /**
     * Get a [T] based on its corresponding [Identifier], or null.
     *
     * @param id The [Identifier] associated with this [T]
     * @return The object, or null if it could not be found.
     */
    fun getOrNull(id: Identifier) : KClass<T>? {
        return values[id]
    }

    /**
     * Get all the used IDs of this map.
     *
     * @return A set of the [Identifier]s.
     */
    fun getAllIds() : Set<Identifier> {
        return values.keys
    }
}