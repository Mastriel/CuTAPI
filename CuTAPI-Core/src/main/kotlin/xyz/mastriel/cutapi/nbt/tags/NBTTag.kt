package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTCompound

interface NBTTag<T> {
    fun store(value: T)

    fun get(): T

    val default: T?

    val key: String

    var compound: NBTCompound
}