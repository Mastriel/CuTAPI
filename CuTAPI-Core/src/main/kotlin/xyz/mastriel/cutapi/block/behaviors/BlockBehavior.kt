package xyz.mastriel.cutapi.block.behaviors

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.behavior.Behavior
import xyz.mastriel.cutapi.block.CuTPlacedBlock
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier

sealed interface TileBehavior : Identifiable, Behavior {

    fun onLeftClick(player: Player, block: CuTPlacedBlock, event: PlayerInteractEvent) {}
    fun onMiddleClick(player: Player, block: CuTPlacedBlock, event: PlayerInteractEvent) {}
    fun onRightClick(player: Player, block: CuTPlacedBlock, event: PlayerInteractEvent) {}
}

abstract class BlockBehavior(
    override val id: Identifier
) : TileBehavior {

}


abstract class TileEntityBehavior(
    override val id: Identifier
) : TileBehavior {

    open fun onTick(block: CuTPlacedBlock) {}
}