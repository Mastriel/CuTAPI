package xyz.mastriel.cutapi.utils.computable

import xyz.mastriel.cutapi.utils.personalized.ConstantPersonalizedWithDefault
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import kotlin.reflect.KProperty

interface ComputableWithDefault<in E, out T> : Computable<E, T> {
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): (E?) -> T {
        return ::withEntity
    }

    fun getDefault(): T

    override infix fun <R> alterResult(block: (@UnsafeVariance E, T) -> R) : Computable<E, R> {
        return AlteredComputable(this, block)
    }

    infix fun <R> alterResult(block: (T) -> R) : ComputableWithDefault<E, R> {
        return AlteredComputableWithDefault(this, block)
    }
}


infix fun <E, T> ComputableWithDefault<E, T>.withEntity(viewer: E?): T {
    return if (viewer == null) getDefault() else withEntity(viewer)
}

infix fun <E, T> Computable<E, T>.or(constantValue: T): ComputableWithDefault<E, T> {
    if (this is ComputableWithDefault<E, T>) return this

    return ChildComputableWithDefault(constantValue, this)
}

fun <E, T> computable(constantValue: T): ComputableWithDefault<E, T> {
    return ConstantComputableWithDefault(constantValue)
}