package xyz.mastriel.cutapi.registry

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.Plugin

private typealias HookFunction<T> = HookContext<T>.() -> Unit

enum class HookPriority(val number: Byte) {
    FIRST(0),
    MIDDLE(1),
    LAST(2),
    /** Don't make modifications at this level. */
    READONLY(3)
}

data class HookContext<T: Identifiable>(val registry: IdentifierRegistry<T>, val item: T, var preventRegister: Boolean)

/**
 * A map of [Identifier] to [T]. This is used in keeping a registry of all items, blocks, etc.
 *
 * @param T The identifiable that is being tracked.
 */
open class IdentifierRegistry<T : Identifiable>(val name: String) {
    protected val values = mutableMapOf<Identifier, T>()
    protected val hooks = mutableListOf<Pair<HookFunction<T>, HookPriority>>()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     */
    open fun register(item: T): T {
        if (values.containsKey(item.id)) error("Two Identifiables cannot have the same ID in the same registry.")
        values[item.id] = item

        for ((hook) in hooks.sortedBy { it.second.number }) {
            val context = HookContext(this, item, false)
            hook(context)
            if (context.preventRegister) {
                values.remove(item.id)
                Plugin.info("[REGISTRY] ${item.id} failed to add to '$name' because of a hook.")
                return item
            }
        }
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
     * Get all the used IDs of this registry.
     *
     * @return A set of the [Identifier]s.
     */
    open fun getAllIds(): Set<Identifier> {
        return values.keys
    }

    /**
     * Get all the identifiables of this registry.
     *
     * @return A set of the [Identifiable]s.
     */
    open fun getAllValues(): Set<T> {
        return values.values.toSet()
    }

    /**
     * Check if this ID exists in this registry.
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


    /**
     * A hook will happen right after an identifiable is registered to this registry.
     * If you set [HookContext.preventRegister] to true, then it will immediately unregister
     * the identifiable and prevent any other hooks from running.
     */
    open fun addHook(priority: HookPriority, func: HookFunction<T>) {
        hooks += func to priority
    }

}
