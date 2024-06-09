package xyz.mastriel.cutapi.block.behaviors

import org.bukkit.entity.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.registry.*

public interface TileBehavior : Identifiable, Behavior {

    public fun onLeftClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {}
    public fun onMiddleClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {}
    public fun onRightClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {}
}

public abstract class BlockBehavior(
    override val id: Identifier
) : TileBehavior {

}


public abstract class TileEntityBehavior(
    override val id: Identifier
) : TileBehavior {

    public open fun onLoadedTick(block: CuTPlacedTile) {}
}