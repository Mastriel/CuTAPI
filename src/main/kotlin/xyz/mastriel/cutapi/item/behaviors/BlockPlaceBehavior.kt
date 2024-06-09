package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.block.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public class BlockPlaceBehavior(
    public val tile: CustomTile<*>,
    public val consumesItem: Boolean = true
) : ItemBehavior(id(Plugin, "block_place").appendSubId(tile.id.key)) {

    override fun onPlace(player: Player, item: CuTItemStack, location: Location, event: BlockPlaceEvent) {
        super.onPlace(player, item, location, event)
    }
}