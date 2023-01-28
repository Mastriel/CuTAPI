package xyz.mastriel.cutapi.player

import org.bukkit.entity.Player

interface PlayerComponent {

    fun onAdd(player: Player) = Unit
    fun onRemove(player: Player) = Unit
}