package xyz.mastriel.cutapi.block.behaviors

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.behavior.Behavior
import xyz.mastriel.cutapi.block.CuTPlacedTile
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier

interface TileBehavior : Identifiable, Behavior {

    fun onLeftClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {}
    fun onMiddleClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {}
    fun onRightClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {}
}

abstract class BlockBehavior(
    override val id: Identifier
) : TileBehavior {

}


abstract class TileEntityBehavior(
    override val id: Identifier
) : TileBehavior {

    open fun onLoadedTick(block: CuTPlacedTile) {}
}