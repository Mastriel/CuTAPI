package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player

internal class ConstantPersonalizedWithDefault<T>(val constantDefault: T) : PersonalizedWithDefault<T> {
    override fun getDefault() = constantDefault
    override fun withViewer(viewer: Player): T = constantDefault
}