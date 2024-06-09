package xyz.mastriel.cutapi.pdc.tags

public interface Tag<T> {

    public fun store(value: T)
    public fun get(): T

    public val default: T?
    public val key: String
    public var container: TagContainer

    public companion object {
        public const val NULL: String = "\u0000NULL"
    }
}
