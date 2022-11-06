package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player

class AlteredPersonalizedWithDefault<T>(val previous: PersonalizedWithDefault<T>, val alter: (player: Player, value: T) -> T) : PersonalizedWithDefault<T> {
    override fun withViewer(viewer: Player): T {
        return alter(viewer, previous withViewer viewer)
    }

    override fun getDefault(): T {
        return previous.getDefault()
    }
}