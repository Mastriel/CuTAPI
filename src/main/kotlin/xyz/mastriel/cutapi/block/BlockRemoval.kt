@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import org.bukkit.craftbukkit.CraftWorld
import xyz.mastriel.cutapi.block.inventory.BlockInventoryStore
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockDisplayManager
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockTypes

/**
 * Permanently removes this custom tile without producing break drops or experience. Native tile
 * removal callbacks still run, allowing systems to close viewers and release runtime state.
 *
 * @return `true` when the tile was still present and Minecraft accepted its removal.
 */
public fun CuTPlacedTile.removeWithoutDrops(): Boolean {
    if (NativeBlockTypes.definition(handle) == null) return false

    snapshotForRemoval()
    val level = (handle.world as CraftWorld).handle
    val position = BlockPos(handle.x, handle.y, handle.z)
    val removed = level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
    if (!removed) return false

    if (this is CuTPlacedTileEntity) BlockInventoryStore.remove(this)
    NativeBlockDisplayManager.refresh(handle)
    return true
}
