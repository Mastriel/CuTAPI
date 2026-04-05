package xyz.mastriel.cutapi.utils

import org.bukkit.entity.*

internal open class PlayerAttachedValue<T>(
    protected val producer: (Player) -> T
) : AttachedValue<Player, T> {

    protected val mapping = weakPlayerMapOf<T>()

    override fun get(entity: Player): T {
        return mapping[entity] ?: producer(entity).also { mapping[entity] = it }
    }
}

internal class MutablePlayerAttachedValue<T>(
    producer: (Player) -> T
) : PlayerAttachedValue<T>(producer), MutableAttachedValue<Player, T> {
    override fun set(entity: Player, value: T) {
        mapping[entity] = value
    }
}

public fun <T> playerAttachedValue(producer: (Player) -> T): AttachedValue<Player, T> {
    return PlayerAttachedValue(producer)
}

public fun <T> mutablePlayerAttachedValue(producer: (Player) -> T): MutableAttachedValue<Player, T> {
    return MutablePlayerAttachedValue(producer)
}