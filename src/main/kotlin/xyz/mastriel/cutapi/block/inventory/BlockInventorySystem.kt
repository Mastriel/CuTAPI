package xyz.mastriel.cutapi.block.inventory

import org.bukkit.GameMode
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkUnloadEvent
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.block.BlockDropContext
import xyz.mastriel.cutapi.block.BlockInteractContext
import xyz.mastriel.cutapi.block.BlockExplosionContext
import xyz.mastriel.cutapi.block.BlockPostBreakContext
import xyz.mastriel.cutapi.block.BlockPreBreakContext
import xyz.mastriel.cutapi.block.CuTPlacedTileEntity
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.block.TileRemoveContext
import xyz.mastriel.cutapi.block.TileSaveContext
import xyz.mastriel.cutapi.block.TileSystem
import xyz.mastriel.cutapi.block.TileUnloadContext
import xyz.mastriel.cutapi.gui.GuiCloseReason
import xyz.mastriel.cutapi.gui.GuiOpenResult
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.attachments.BlockPlaceAttachment
import xyz.mastriel.cutapi.item.attachments.Unstackable
import xyz.mastriel.cutapi.item.getAttachmentOrNull
import xyz.mastriel.cutapi.item.setAttachment
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id

internal object BlockInventorySystem : TileSystem {
    override val id: Identifier = id("cutapi:block_inventory/system")

    override fun tilePrerequisite(tile: CuTPlacedTileEntity): Boolean =
        (tile.type as? CustomTileEntity<*>)?.descriptor?.inventory != null

    override fun onRightClick(context: BlockInteractContext) {
        val tile = context.tile as CuTPlacedTileEntity
        val definition = tile.requireInventory().definition
        if (!definition.openOnRightClick || definition.presentation == null) return
        if (tile.openInventory(context.player) is GuiOpenResult.Opened) {
            context.event.setUseInteractedBlock(Event.Result.DENY)
            context.event.setUseItemInHand(Event.Result.DENY)
        }
    }

    override fun onPreBreak(context: BlockPreBreakContext) {
        (context.tile as CuTPlacedTileEntity).inventoryOrNull?.flushPending()
    }

    override fun onDrops(context: BlockDropContext) {
        val tile = context.tile as CuTPlacedTileEntity
        val inventory = tile.inventoryOrNull ?: return
        val definition = inventory.definition
        val snapshot = inventory.snapshot()
        val wrongToolDeletes = definition.requireCorrectToolForContents &&
            context.player?.gameMode in setOf(GameMode.SURVIVAL, GameMode.ADVENTURE) &&
            !context.correctToolUsed

        if (!wrongToolDeletes) {
            when (definition.breakPolicy) {
                BlockInventoryBreakPolicy.DropContents -> {
                    snapshot?.entries?.values?.forEach { context.drops += it.clone() }
                }
                BlockInventoryBreakPolicy.KeepContents -> {
                    context.drops.removeIf { drop ->
                        runCatching {
                            CuTItemStack.wrap(drop).getAttachmentOrNull(BlockPlaceAttachment)?.tileId == tile.type.id
                        }.getOrDefault(false)
                    }
                    if (snapshot != null) {
                        val placement = context.requirePlacementItem().also { it.amount = 1 }
                        val wrapped = CuTItemStack.wrap(placement)
                        wrapped.setAttachment(snapshot)
                        wrapped.setAttachment(Unstackable)
                        context.drops += placement
                    }
                }
                BlockInventoryBreakPolicy.DeleteContents -> Unit
            }
        } else if (definition.breakPolicy == BlockInventoryBreakPolicy.KeepContents) {
            context.drops.removeIf { drop ->
                runCatching {
                    CuTItemStack.wrap(drop).getAttachmentOrNull(BlockPlaceAttachment)?.tileId == tile.type.id
                }.getOrDefault(false)
            }
        }
        inventory.clear()
    }

    override fun onExploded(context: BlockExplosionContext) {
        val tile = context.tile as CuTPlacedTileEntity
        val inventory = tile.inventoryOrNull ?: return
        inventory.flushPending()
        CuTAPI.guiManager.closeForBlock(tile, GuiCloseReason.BlockRemoved)
        val snapshot = inventory.snapshot()
        when (inventory.definition.breakPolicy) {
            BlockInventoryBreakPolicy.DropContents -> snapshot?.entries?.values.orEmpty().forEach { stack ->
                tile.handle.world.dropItemNaturally(tile.location, stack.clone())
            }
            BlockInventoryBreakPolicy.KeepContents -> if (snapshot != null) {
                val placement = tile.type.placementItemOrNull()?.createItemStack()?.vanilla()
                    ?: error("KeepContents tile ${tile.type.id} has no placement item.")
                val wrapped = CuTItemStack.wrap(placement)
                wrapped.setAttachment(snapshot)
                wrapped.setAttachment(Unstackable)
                tile.handle.world.dropItemNaturally(tile.location, placement)
            }
            BlockInventoryBreakPolicy.DeleteContents -> Unit
        }
        inventory.clear()
        BlockInventoryStore.remove(tile)
    }

    override fun onPostBreak(context: BlockPostBreakContext) {
        BlockInventoryStore.remove(context.tile as CuTPlacedTileEntity)
    }

    override fun onBeforeSave(context: TileSaveContext) {
        context.tileEntity.inventoryOrNull?.flushPending()
    }

    override fun onUnloaded(context: TileUnloadContext) {
        context.tileEntity.inventoryOrNull?.flushPending()
    }

    override fun onRemoved(context: TileRemoveContext) {
        CuTAPI.guiManager.closeForBlock(context.tileEntity, GuiCloseReason.BlockRemoved)
    }
}

internal object BlockInventoryLifecycleListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        BlockInventoryStore.removeChunk(event.world.uid, event.chunk.x, event.chunk.z).forEach { inventory ->
            inventory.flushPending()
            CuTAPI.guiManager.closeForBlock(inventory.tile, GuiCloseReason.BlockUnloaded)
        }
    }
}
