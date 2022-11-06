package xyz.mastriel.cutapi.block.behaviors

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.behavior.Behavior
import xyz.mastriel.cutapi.block.TileEntity
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier

abstract class BlockBehavior(
    override val id: Identifier
) : Identifiable, Behavior {

    open fun onLeftClick(player: Player, tileEntity: TileEntity, event: PlayerInteractEvent) {}
    open fun onMiddleClick(player: Player, tileEntity: TileEntity, event: PlayerInteractEvent) {}
    open fun onRightClick(player: Player, tileEntity: TileEntity, event: PlayerInteractEvent) {}

    open fun onTick(tileEntity: TileEntity) {}

}