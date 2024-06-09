package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.*
import xyz.mastriel.cutapi.utils.computable.*

public class AlteredPersonalized<T, R>(previous: Personalized<T>, alter: (player: Player, value: T) -> R) :
    Personalized<R>, AlteredComputable<Player, T, R>(previous, alter) {

    override fun withViewer(viewer: Player): R = super<AlteredComputable>.withEntity(viewer)
    override fun withEntity(entity: Player): R = super<AlteredComputable>.withEntity(entity)


}