package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.Player
import xyz.mastriel.cutapi.utils.computable.Computable


interface Personalized<out T> : Computable<Player, T> {

    infix fun withViewer(viewer: Player): T

    override fun withEntity(entity: Player): T = withEntity(entity)

    override infix fun <R> alterResult(block: (Player, T) -> R) : Personalized<R> {
        return AlteredPersonalized(this, block)
    }
}

fun <T> personalized(block: Personalized<T>.(Player) -> T): Personalized<T> {
    return SimplePersonalized(block)
}


