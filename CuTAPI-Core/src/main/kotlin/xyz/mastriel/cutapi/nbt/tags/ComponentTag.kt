package xyz.mastriel.cutapi.nbt.tags

import kotlin.reflect.KProperty

interface ComponentTag<T> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)

    fun store(value: T)

    fun get() : T
}