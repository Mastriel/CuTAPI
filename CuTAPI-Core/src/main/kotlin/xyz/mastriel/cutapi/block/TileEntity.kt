package xyz.mastriel.cutapi.block

import org.bukkit.Location
import xyz.mastriel.cutapi.pdc.tags.BlockTagContainer

/**
 * An instance of a tile entity.
 */
class TileEntity(location: Location) : BlockTagContainer(location.block) {

}