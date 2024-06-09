package xyz.mastriel.cutapi.utils.computable

import kotlin.reflect.*

public interface Computable<in E, out T> {

    public operator fun getValue(thisRef: Any?, property: KProperty<*>): (E) -> T {
        return ::withEntity
    }

    public infix fun withEntity(entity: E): T


    public infix fun <R> alterResult(block: (@UnsafeVariance E, T) -> R): Computable<E, R> {
        return AlteredComputable(this, block)
    }
}

public fun <E, T> computable(block: Computable<E, T>.(E) -> T): Computable<E, T> {
    return SimpleComputable(block)
}