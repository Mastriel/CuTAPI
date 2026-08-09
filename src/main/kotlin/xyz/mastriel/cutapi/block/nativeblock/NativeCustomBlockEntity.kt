@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import org.bukkit.craftbukkit.CraftWorld
import xyz.mastriel.cutapi.block.BlockSystem
import xyz.mastriel.cutapi.block.CuTPlacedTileEntity
import xyz.mastriel.cutapi.block.TileDataLoadContext
import xyz.mastriel.cutapi.block.TileLoadContext
import xyz.mastriel.cutapi.block.TileRemoveContext
import xyz.mastriel.cutapi.block.TileSaveContext
import xyz.mastriel.cutapi.block.TileSystem
import xyz.mastriel.cutapi.block.TileTickContext
import xyz.mastriel.cutapi.block.TileUnloadContext
import xyz.mastriel.cutapi.block.CustomTileEntity

internal class NativeCustomBlockEntity(
    internal val definition: CustomTileEntity<*>,
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(NativeBlockTypes.getBlockEntityType(definition.id), pos, state) {
    private var lifecycleLoaded: Boolean = false

    fun tick() {
        val tile = placedTile() ?: return
        if (!lifecycleLoaded) {
            lifecycleLoaded = true
            tileSystems(tile).forEach { system ->
                system.onDataLoaded(TileDataLoadContext(tile))
                system.onLoaded(TileLoadContext(tile))
            }
        }
        tileSystems(tile).forEach { it.onTick(TileTickContext(tile)) }
    }

    override fun saveAdditional(output: ValueOutput) {
        placedTile()?.let { tile ->
            tileSystems(tile).forEach { it.onBeforeSave(TileSaveContext(tile)) }
        }
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        lifecycleLoaded = false
    }

    override fun setRemoved() {
        placedTile()?.let { tile ->
            tileSystems(tile).forEach { system ->
                system.onUnloaded(TileUnloadContext(tile))
            }
        }
        super.setRemoved()
    }

    override fun preRemoveSideEffects(pos: BlockPos, state: BlockState) {
        placedTile()?.let { tile ->
            tile.snapshotForRemoval()
            tileSystems(tile).forEach { it.onRemoved(TileRemoveContext(tile)) }
        }
        super.preRemoveSideEffects(pos, state)
    }

    private fun placedTile(): CuTPlacedTileEntity? {
        if (NativeBlockLifecycle.state != NativeBlockState.Active) return null
        val currentLevel = level as? net.minecraft.server.level.ServerLevel ?: return null
        val world = currentLevel.world as? CraftWorld ?: return null
        return NativeBlockTypes.placedTile(world.getBlockAt(blockPos.x, blockPos.y, blockPos.z)) as? CuTPlacedTileEntity
    }

    private fun tileSystems(tile: CuTPlacedTileEntity): List<TileSystem> =
        BlockSystem.systemsFor(tile).filterIsInstance<TileSystem>()
}
