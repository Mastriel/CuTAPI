package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.entity.*
import xyz.mastriel.cutapi.utils.computable.*

internal class AlteredPersonalizedWithDefault<T, R>(
    previous: PersonalizedWithDefault<T>,
    alter: (value: T) -> R
) : AlteredComputableWithDefault<Player, T, R>(previous, alter),
    PersonalizedWithDefault<R> {

    override fun withEntity(entity: Player): R {
        return alter(previous withEntity entity)
    }

    override fun withViewer(viewer: Player): R {
        return withEntity(viewer)
    }

    override fun getDefault(): R {
        return alter(previous.getDefault())
    }
}