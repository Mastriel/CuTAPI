package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.*
import xyz.mastriel.cutapi.utils.computable.*

@Suppress("UNCHECKED_CAST")
internal class SimplePersonalized<T>(block: Personalized<T>.(Player) -> T) :
    SimpleComputable<Player, T>(block as Computable<Player, T>.(Player) -> T),
    Personalized<T> {

    override fun withViewer(viewer: Player): T = super<SimpleComputable>.withEntity(viewer)
    override fun withEntity(entity: Player): T = super<SimpleComputable>.withEntity(entity)
}