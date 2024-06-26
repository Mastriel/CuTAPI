package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.*
import xyz.mastriel.cutapi.utils.computable.*
import kotlin.reflect.*

public interface PersonalizedWithDefault<out T> : Personalized<T>, ComputableWithDefault<Player, T> {
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): (Player?) -> T {
        return ::withViewer
    }

    override fun getDefault(): T

    override infix fun <R> alterResult(block: (Player, T) -> R): Personalized<R> {
        return AlteredPersonalized(this, block)
    }

    override infix fun <R> alterResult(block: (T) -> R): PersonalizedWithDefault<R> {
        return AlteredPersonalizedWithDefault(this, block)
    }


}


public infix fun <T> PersonalizedWithDefault<T>.withViewer(viewer: Player?): T {
    return if (viewer == null) getDefault() else withViewer(viewer)
}

public infix fun <T> Personalized<T>.or(constantValue: T): PersonalizedWithDefault<T> {
    if (this is PersonalizedWithDefault<T>) return this

    return ChildPersonalizedWithDefault(constantValue, this)
}

public fun <T> personalized(constantValue: T): PersonalizedWithDefault<T> {
    return ConstantPersonalizedWithDefault(constantValue)
}