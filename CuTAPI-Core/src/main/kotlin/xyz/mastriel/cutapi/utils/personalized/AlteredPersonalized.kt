package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player

class AlteredPersonalized<T>(val previous: Personalized<T>, val alter: (player: Player, value: T) -> T) : Personalized<T> {
    override fun withViewer(viewer: Player): T {
        return alter(viewer, previous withViewer viewer)
    }
}