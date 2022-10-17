package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.Plugin

/**
 * A map of [Identifier] to [T]. This is used in keeping a registry of all items, blocks, etc.
 *
 * @param T The identifiable that is being tracked.
 */
open class IdentifierRegistry<T : Identifiable> {
    protected val values = mutableMapOf<Identifier, T>()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     */
    open fun register(item: T): T {
        if (values.containsKey(item.id)) error("Two Identifiables cannot have the same ID in the same registry.")
        values[item.id] = item
        Plugin.info("[REGISTRY] ${item.id} added to a registry.")
        return item
    }

    /**
     * Get a [T] based on its corresponding [Identifier].
     *
     * @param id The [Identifier] associated with this [T]
     * @return The object
     * @throws IllegalStateException If this could not be found.
     */
    open fun get(id: Identifier): T {
        return getOrNull(id) ?: error("Identifier points to nothing.")
    }

    /**
     * Get a [T] based on its corresponding [Identifier], or null.
     *
     * @param id The [Identifier] associated with this [T]
     * @return The object, or null if it could not be found.
     */
    open fun getOrNull(id: Identifier): T? {
        return values[id]
    }

    /**
     * Get all the used IDs of this map.
     *
     * @return A set of the [Identifier]s.
     */
    fun getAllIds(): Set<Identifier> {
        return values.keys
    }
}
