package xyz.mastriel.cutapi.player

import org.bukkit.entity.Player
import xyz.mastriel.cutapi.pdc.tags.PDCTagContainer
import xyz.mastriel.cutapi.pdc.tags.TagContainer

class PlayerTagContainer(private val player: Player) : PDCTagContainer(player.persistentDataContainer)

val Player.tags : TagContainer get() = PlayerTagContainer(this)
