package xyz.mastriel.cutapi.registry

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.Plugin

/**
 * A map of [Identifier] to [T]. This is used in keeping a registry of all items, blocks, etc.
 *
 * @param T The identifiable that is being tracked.
 */
open class IdentifierRegistry<T : Identifiable>(val name: String) {
    protected val values = mutableMapOf<Identifier, T>()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     */
    open fun register(item: T): T {
        if (values.containsKey(item.id)) error("Two Identifiables cannot have the same ID in the same registry.")
        values[item.id] = item
        Plugin.info("[REGISTRY] ${item.id} added to '$name'.")
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
    open fun getAllIds(): Set<Identifier> {
        return values.keys
    }

    /**
     * Get all the used IDs of this map.
     *
     * @return A set of the [Identifier]s.
     */
    open fun getAllValues(): Set<T> {
        return values.values.toSet()
    }

    /**
     *
     */
    open fun has(id: Identifier?) : Boolean {
        return id in values.keys
    }

    /**
     * Gets all entries in this registry by this particular plugin.
     */
    open fun getBy(plugin: Plugin) : Set<T> {
        return values.values.filter { it.id.plugin == plugin }.toSet()
    }

}
