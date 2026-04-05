package xyz.mastriel.cutapi.utils

import kotlin.properties.*
import kotlin.reflect.*

/**
 * Represents a generic interface for associating a value of type [T] with an entity of type [E].
 *
 * This interface extends [ReadOnlyProperty], allowing it to be used in property delegates.
 *
 * @param E The type of the entity to which the value is attached.
 * @param T The type of the value attached to the entity.
 */
public interface AttachedValue<E, T> : ReadOnlyProperty<E, T> {
    public fun get(entity: E): T

    override fun getValue(thisRef: E, property: KProperty<*>): T {
        return get(thisRef)
    }
}


public interface MutableAttachedValue<E, T> : AttachedValue<E, T>, ReadWriteProperty<E, T> {
    public fun set(entity: E, value: T)

    override fun getValue(thisRef: E, property: KProperty<*>): T {
        return super.getValue(thisRef, property)
    }

    override fun setValue(thisRef: E, property: KProperty<*>, value: T) {
        set(thisRef, value)
    }
}
