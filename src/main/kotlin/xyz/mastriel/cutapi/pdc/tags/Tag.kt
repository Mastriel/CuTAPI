package xyz.mastriel.cutapi.pdc.tags

interface Tag<T> {

    fun store(value: T)
    fun get(): T

    val default: T?
    val key: String
    var container: TagContainer

    companion object {
        const val NULL = "\u0000NULL"
    }
}
