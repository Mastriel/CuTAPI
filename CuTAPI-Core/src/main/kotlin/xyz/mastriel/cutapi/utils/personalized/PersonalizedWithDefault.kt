package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player
import kotlin.reflect.KProperty

interface PersonalizedWithDefault<out T> : Personalized<T> {
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): (Player?) -> T {
        return ::withViewer
    }

    fun getDefault(): T

    override infix fun alterResult(block: (Player, T) -> @UnsafeVariance T) : PersonalizedWithDefault<T> {
        return AlteredPersonalizedWithDefault(this, block)
    }
}


infix fun <T> PersonalizedWithDefault<T>.withViewer(viewer: Player?): T {
    return if (viewer == null) getDefault() else withViewer(viewer)
}

infix fun <T> Personalized<T>.or(constantValue: T): PersonalizedWithDefault<T> {
    if (this is PersonalizedWithDefault<T>) return this

    return ChildPersonalizedWithDefault(constantValue, this)
}

fun <T> personalized(constantValue: T): PersonalizedWithDefault<T> {
    return ConstantPersonalizedWithDefault(constantValue)
}