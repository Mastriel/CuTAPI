package xyz.mastriel.cutapi.utils

import org.bukkit.entity.Player

interface Personalized<T> {
    infix fun withViewer(viewer: Player) : T
}

fun <T> personalized(constantValue: T) : Personalized<T> {
    return object : Personalized<T> {
        override fun withViewer(viewer: Player) =
            constantValue
    }
}

fun <T> personalized(block: Personalized<T>.(Player) -> T) : Personalized<T> {
    return object : Personalized<T> {
        override fun withViewer(viewer: Player) =
            block(viewer)
    }
}

