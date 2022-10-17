package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player

internal class SimplePersonalized<T>(val block: Personalized<T>.(Player) -> T) : Personalized<T> {
    override fun withViewer(viewer: Player): T = block(viewer)
}