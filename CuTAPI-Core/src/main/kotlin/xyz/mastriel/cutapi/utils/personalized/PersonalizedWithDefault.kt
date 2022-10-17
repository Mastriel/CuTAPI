package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player
import kotlin.reflect.KProperty

interface PersonalizedWithDefault<T> : Personalized<T> {
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): (Player?) -> T {
        return ::withViewer
    }

    fun getDefault(): T
}


infix fun <T> PersonalizedWithDefault<T>.withViewer(viewer: Player?): T {
    return if (viewer == null) getDefault() else withViewer(viewer)
}

infix fun <T> Personalized<T>.or(constantValue: T): PersonalizedWithDefault<T> {
    return ChildPersonalizedWithDefault(constantValue, this)
}

fun <T> personalized(constantValue: T): PersonalizedWithDefault<T> {
    return ConstantPersonalizedWithDefault(constantValue)
}