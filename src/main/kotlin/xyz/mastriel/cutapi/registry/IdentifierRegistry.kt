package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.CuTPlugin
import xyz.mastriel.cutapi.Plugin
import java.lang.ref.WeakReference

private typealias HookFunction<T> = HookContext<T>.() -> Unit

public enum class HookPriority(public val number: Byte) {
    FIRST(0),
    MIDDLE(1),
    LAST(2),

    /** Don't make modifications at this level pretty please */
    READONLY(3)
}

public data class HookContext<T : Identifiable>(val registry: IdentifierRegistry<T>, val item: T, var preventRegister: Boolean)

/**
 * A map of [Identifier] to [T]. This is used in keeping a registry of all items, blocks, etc.
 *
 * @param T The identifiable that is being tracked.
 */
public open class IdentifierRegistry<T : Identifiable>(public val name: String) {
    protected val values: MutableMap<Identifier, T> = mutableMapOf()
    protected val hooks: MutableList<Pair<HookContext<T>.() -> Unit, HookPriority>> =
        mutableListOf()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     */
    public open fun register(item: T): T {
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
    public open fun unregister(item: T): Unit = unregister(item.id)

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
    public open fun unregister(id: Identifier) {
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
    public open fun get(id: Identifier): T {
        return getOrNull(id) ?: error("Identifier (${id}) points to nothing in '${name}'.")
    }

    /**
     * Get a [T] based on its corresponding [Identifier], or null.
     *
     * @param id The [Identifier] associated with this [T]
     * @return The object, or null if it could not be found.
     */
    public open fun getOrNull(id: Identifier): T? {
        return values[id]
    }

    /**
     * Get all the used IDs of this registry.
     *
     * @return A set of the [Identifier]s.
     */
    public open fun getAllIds(): Set<Identifier> {
        return values.keys
    }

    /**
     * Get all the identifiables of this registry.
     *
     * @return A set of the [Identifiable]s.
     */
    public open fun getAllValues(): Set<T> {
        return values.values.toSet()
    }

    /**
     * Check if this ID exists in this registry.
     */
    public open fun has(id: Identifier?): Boolean {
        return id in values.keys
    }

    /**
     * Gets all entries in this registry by this particular plugin.
     */
    public open fun getBy(plugin: CuTPlugin): Set<T> {
        return values.values.filter { it.id.plugin == plugin }.toSet()
    }


    /**
     * A hook will happen right after an identifiable is registered to this registry.
     * If you set [HookContext.preventRegister] to true, then it will immediately unregister
     * the identifiable and prevent any other hooks from running.
     */
    public open fun addHook(priority: HookPriority, func: HookFunction<T>) {
        hooks += func to priority
    }

    internal companion object {
        // uses weak references, although registries should probably not be
        // garbage collected at any point and should always have a strong reference
        private val usedRegistries = mutableListOf<WeakReference<IdentifierRegistry<*>>>()

        internal fun unregisterPluginGlobally(plugin: CuTPlugin) {
            usedRegistries.mapNotNull { it.get() }.forEach { registry ->
                registry.values.keys.filter { it.plugin == plugin }.forEach { registry.unregister(it) }
            }
        }
    }
}
