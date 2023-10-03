package xyz.mastriel.cutapi.item.behaviors

import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.registry.id

class BlockPlaceBehavior(val tile: CustomTile) : ItemBehavior(id(Plugin, "block_place").appendSubId(tile.id.key)) {
}