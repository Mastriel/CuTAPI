package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player

internal class ChildPersonalizedWithDefault<T>(val constantDefault: T, val parent: Personalized<T>) :
    PersonalizedWithDefault<T> {

    override fun getDefault() = constantDefault
    override fun withViewer(viewer: Player): T = parent.withViewer(viewer)
}