package xyz.mastriel.cutapi.registry

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.Plugin
import java.lang.ref.WeakReference

private typealias HookFunction<T> = HookContext<T>.() -> Unit

enum class HookPriority(val number: Byte) {
    FIRST(0),
    MIDDLE(1),
    LAST(2),
    /** Don't make modifications at this level pretty please */
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
        // add this to the list of used registries if it's not already there. keeps track of all registries
        // for when a plugin is disabled.
        if (!usedRegistries.any { it.get() == this }) {
            usedRegistries += WeakReference(this)
        }

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
     * Remove an object from this registry.
     *
     * This can be dangerous if you're not handling this properly. It is not recommended
     * to treat a registry as a place where you should commonly remove objects from, like
     * a normal hashmap. This should only be used in situations where you can run into errors
     * if you __dont__ unregister an object, like if a plugin is disabled that owns the [item].
     *
     * P.S. When a plugin is disabled, all identifiers registered to any registry is automatically
     * unregistered, so you don't need to do that manually.
     *
     * @param item The object being removed.
     */
    open fun unregister(item: T) = unregister(item.id)

    /**
     * Remove an object from this registry.
     *
     * This can be dangerous if you're not handling this properly. It is not recommended
     * to treat a registry as a place where you should commonly remove objects from, like
     * a normal hashmap. This should only be used in situations where you can run into errors
     * if you __don't__ unregister an object, like if a plugin is disabled that owns the [id].
     *
     * P.S. When a plugin is disabled, all identifiers registered to any registry is automatically
     * unregistered, so you don't need to do that manually.
     *
     * @param id The object being removed.
     */
    open fun unregister(id: Identifier) {
        if (!values.containsKey(id)) {
            Plugin.warn("[REGISTRY] $id tried to be removed from '${this.name}', but it doesn't exist in this registry.")
            return
        }
        values.remove(id)

        // remove this registry from the list of used registries if this is now empty
        if (this.values.isEmpty()) {
            usedRegistries.removeIf { it.get() == this }
        }

        Plugin.info("[REGISTRY] $id removed from '$name'.")
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



    internal companion object {
        // uses weak references, although registries should probably not be
        // garbage collected at any point and should always have a strong reference
        private val usedRegistries = mutableListOf<WeakReference<IdentifierRegistry<*>>>()

        internal fun unregisterPluginGlobally(plugin: Plugin) {
            usedRegistries.mapNotNull { it.get() }.forEach { registry ->
                registry.values.keys.filter { it.plugin == plugin }.forEach { registry.unregister(it) }
            }
        }
    }
}
