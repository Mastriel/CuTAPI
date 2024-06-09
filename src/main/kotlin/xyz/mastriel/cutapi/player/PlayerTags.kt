package xyz.mastriel.cutapi.player

import org.bukkit.entity.*
import xyz.mastriel.cutapi.pdc.tags.*

public class PlayerTagContainer(private val player: Player) : PDCTagContainer(player.persistentDataContainer)

public val Player.tags : TagContainer get() = PlayerTagContainer(this)
