package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.*
import xyz.mastriel.cutapi.utils.computable.*

internal class ConstantPersonalizedWithDefault<T>(constantDefault: T) :
    PersonalizedWithDefault<T>,
    ConstantComputableWithDefault<Player, T>(constantDefault) {

    override fun withViewer(viewer: Player): T = super<ConstantComputableWithDefault>.withEntity(viewer)
    override fun withEntity(entity: Player): T = super<ConstantComputableWithDefault>.withEntity(entity)
    }