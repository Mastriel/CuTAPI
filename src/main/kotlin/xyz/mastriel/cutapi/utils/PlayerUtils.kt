package xyz.mastriel.cutapi.utils

import org.bukkit.*
import org.bukkit.entity.*
import java.util.*
import kotlin.properties.*
import kotlin.reflect.*


public fun onlinePlayers(): List<Player> = Bukkit.getOnlinePlayers()
    .filterNotNull()
    .toList()

public fun playerNameList(): List<String> = onlinePlayers().map(Player::getName)


@JvmInline
public value class PlayerUUID(public val uuid: UUID) : ReadOnlyProperty<Any?, Player?> {
    public fun toPlayer(): Player? = Bukkit.getPlayer(uuid)

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): Player? {
        return toPlayer()
    }

}

public val Player.playerUUID: PlayerUUID get() = PlayerUUID(uniqueId)