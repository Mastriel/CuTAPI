package xyz.mastriel.cutapi.block

import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.block.CustomBlockManager.Companion.isCustom
import xyz.mastriel.cutapi.block.CustomBlockManager.Companion.wrap

internal object BlockRuntimeEvents : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (!block.isCustom) return
        val tile = block.wrap<CuTPlacedTile>() ?: return
        val context = BlockInteractContext(tile, event.player, event)
        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> BlockSystem.dispatch(tile) { it.onLeftClick(context) }
            Action.RIGHT_CLICK_BLOCK -> {
                BlockSystem.dispatch(tile) { it.onRightClick(context) }
                if (!event.player.isSneaking) BlockSystem.dispatch(tile) { it.onInteract(context) }
            }

            else -> Unit
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPhysics(event: BlockPhysicsEvent) {
        if (!event.block.isCustom) return
        val tile = event.block.wrap<CuTPlacedTile>() ?: return
        BlockSystem.dispatch(tile) { it.onNeighborChanged(BlockNeighborChangeContext(tile)) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplosion(event: BlockExplodeEvent) {
        event.blockList().filter { it.isCustom }.forEach { block ->
            val tile = block.wrap<CuTPlacedTile>() ?: return@forEach
            BlockSystem.dispatch(tile) { it.onExploded(BlockExplosionContext(tile, event)) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplosion(event: EntityExplodeEvent) {
        event.blockList().filter { it.isCustom }.forEach { block ->
            val tile = block.wrap<CuTPlacedTile>() ?: return@forEach
            BlockSystem.dispatch(tile) { it.onExploded(BlockExplosionContext(tile, null)) }
        }
    }
}
