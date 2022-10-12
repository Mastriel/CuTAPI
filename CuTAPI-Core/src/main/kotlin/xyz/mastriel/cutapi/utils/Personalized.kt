package xyz.mastriel.cutapi.utils

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


interface PersonalizedLike<V, T> : ReadOnlyProperty<Any?, (V) -> T> {

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): (V) -> T {
        return ::withViewer
    }

    infix fun withViewer(viewer: V) : T
}

interface Personalized<T> : PersonalizedLike<Player, T>

interface PersonalizedWithDefault<T> : PersonalizedLike<Player?, T> {
    fun getDefault() : T
}

infix fun <T> Personalized<T>.or(constantValue: T) : PersonalizedWithDefault<T> {
    return object : PersonalizedWithDefault<T> {
        override fun getDefault(): T = constantValue
        override fun withViewer(viewer: Player?) = if (viewer != null) this@or.withViewer(viewer) else getDefault()
    }
}

fun <T> personalized(constantValue: T) : PersonalizedWithDefault<T> {
    return object : PersonalizedWithDefault<T> {
        override fun getDefault(): T = constantValue
        override fun withViewer(viewer: Player?) =
            constantValue
    }
}

fun <T> personalized(block: Personalized<T>.(Player) -> T) : Personalized<T> {
    return object : Personalized<T> {
        override fun withViewer(viewer: Player) =
            block(viewer)
    }
}

