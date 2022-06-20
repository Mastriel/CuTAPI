package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.Plugin

open class ListRegistry <T: Any> : Iterable<T> {
    protected val values = mutableListOf<T>()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     */
    open fun register(item: T) {
        values += item
        Plugin.info("[REGISTRY] ${item::class.qualifiedName} added to a ListRegistry.")
    }

    /**
     * Get all the registered items in this list.
     *
     * @return A list of all registered items.
     */
    fun getAll() : List<T> {
        return values.toList()
    }

    /**
     * Enable iterating through this ListRegistry.
     */
    override fun iterator(): Iterator<T> =
        values.iterator()


}