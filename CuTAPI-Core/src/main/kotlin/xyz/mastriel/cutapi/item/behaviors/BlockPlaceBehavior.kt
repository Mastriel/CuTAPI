package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockPlaceEvent
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.registry.id

class BlockPlaceBehavior(
    val tile: CustomTile<*>,
    val consumesItem: Boolean = true
) : ItemBehavior(id(Plugin, "block_place").appendSubId(tile.id.key)) {

    override fun onPlace(player: Player, item: CuTItemStack, location: Location, event: BlockPlaceEvent) {
        super.onPlace(player, item, location, event)
    }
}