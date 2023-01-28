package xyz.mastriel.cutapi.utils.computable

import kotlin.reflect.KProperty

interface Computable<in E, out T> {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): (E) -> T {
        return ::withEntity
    }

    infix fun withEntity(entity: E): T


    infix fun <R> alterResult(block: (@UnsafeVariance E, T) -> R) : Computable<E, R> {
        return AlteredComputable(this, block)
    }
}

fun <E, T> computable(block: Computable<E, T>.(E) -> T) : Computable<E, T> {
    return SimpleComputable(block)
}