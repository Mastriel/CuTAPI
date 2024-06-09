package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.Plugin

public open class ListRegistry<T : Any>(public val name: String) : Iterable<T> {
    protected val values: MutableList<T> = mutableListOf()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     * @param name The name of the object being added. Used for logging purposes; not required.
     * The name of [item]'s class will be used if this is null
     */
    public open fun register(vararg item: T, name: String? = null) {
        values += item

        // fixme probably doesnt print right
        Plugin.info("[REGISTRY] ${name ?: ("A " + item::class.qualifiedName)} added to '${this.name}'.")
    }

    public open operator fun plusAssign(item: T) {
        register(item)
    }

    /**
     * Get all the registered items in this list.
     *
     * @return A list of all registered items.
     */
    public fun getAll(): List<T> {
        return values.toList()
    }

    /**
     * Enable iterating through this ListRegistry.
     */
    override fun iterator(): Iterator<T> =
        values.iterator()


}