package xyz.mastriel.cutapi.nbt.tags

interface NBTTag<T> {
    fun store(value: T)

    fun get() : T
}