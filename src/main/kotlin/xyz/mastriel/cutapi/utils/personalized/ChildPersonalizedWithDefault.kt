package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.*
import xyz.mastriel.cutapi.utils.computable.*

internal class ChildPersonalizedWithDefault<T>(constantDefault: T, parent: Personalized<T>) :
    PersonalizedWithDefault<T>,
    ChildComputableWithDefault<Player, T>(constantDefault, parent) {

    override fun withViewer(viewer: Player): T = super<ChildComputableWithDefault>.withEntity(viewer)
    override fun withEntity(entity: Player): T = super<ChildComputableWithDefault>.withEntity(entity)
}