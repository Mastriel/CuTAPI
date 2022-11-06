package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player
import kotlin.reflect.KProperty


interface Personalized<out T> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): (Player) -> T {
        return ::withViewer
    }

    infix fun withViewer(viewer: Player): T

    infix fun alterResult(block: (Player, T) -> @UnsafeVariance T) : Personalized<T> {
        return AlteredPersonalized(this, block)
    }
}

fun <T> personalized(block: Personalized<T>.(Player) -> T): Personalized<T> {
    return SimplePersonalized(block)
}


