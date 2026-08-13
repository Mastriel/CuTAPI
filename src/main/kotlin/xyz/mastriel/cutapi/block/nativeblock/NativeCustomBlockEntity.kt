@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.item.ItemStack as NativeItemStack
import net.minecraft.world.entity.player.Player as NativePlayer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftHumanEntity
import org.bukkit.craftbukkit.inventory.CraftInventory
import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.InventoryHolder
import xyz.mastriel.cutapi.block.inventory.*
import xyz.mastriel.cutapi.nms.bukkit
import xyz.mastriel.cutapi.nms.nms
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
) : BlockEntity(NativeBlockTypes.getBlockEntityType(definition.id), pos, state), WorldlyContainer {
    private var lifecycleLoaded: Boolean = false
    private val viewers: MutableList<HumanEntity> = mutableListOf()
    private var containerMaxStackSize: Int = Container.MAX_STACK
    private val craftInventory: CraftInventory by lazy { CraftInventory(this) }
    private var bukkitHolder: BlockInventoryHolder? = null

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
        val world: CraftWorld = currentLevel.world
        return NativeBlockTypes.placedTile(world.getBlockAt(blockPos.x, blockPos.y, blockPos.z)) as? CuTPlacedTileEntity
    }

    private fun tileSystems(tile: CuTPlacedTileEntity): List<TileSystem> =
        BlockSystem.systemsFor(tile).filterIsInstance<TileSystem>()

    private fun logicalInventory(): BlockInventory? = placedTile()?.inventoryOrNull

    override fun getContainerSize(): Int = logicalInventory()?.size ?: 0

    override fun isEmpty(): Boolean = logicalInventory()?.contents()?.all { it == null || it.type.isAir } ?: true

    override fun getItem(slot: Int): NativeItemStack =
        logicalInventory()?.getItem(slot)?.nms() ?: NativeItemStack.EMPTY

    override fun removeItem(slot: Int, amount: Int): NativeItemStack =
        logicalInventory()?.extractFromAutomation(slot, amount)?.nms() ?: NativeItemStack.EMPTY

    override fun removeItemNoUpdate(slot: Int): NativeItemStack {
        val inventory = logicalInventory() ?: return NativeItemStack.EMPTY
        return inventory.extractFromAutomation(slot, Int.MAX_VALUE)?.nms() ?: NativeItemStack.EMPTY
    }

    override fun setItem(slot: Int, stack: NativeItemStack) {
        logicalInventory()?.setFromAutomation(slot, stack.takeUnless(NativeItemStack::isEmpty)?.bukkit())
    }

    override fun clearContent() {
        logicalInventory()?.clear()
    }

    override fun getMaxStackSize(): Int = containerMaxStackSize

    override fun setMaxStackSize(size: Int) {
        containerMaxStackSize = size.coerceIn(1, Container.MAX_STACK)
    }

    override fun setChanged() {
        super.setChanged()
    }

    override fun stillValid(player: NativePlayer): Boolean =
        !isRemoved && Container.stillValidBlockEntity(this, player)

    override fun getContents(): List<NativeItemStack> =
        NonNullList.createWithCapacity<NativeItemStack>(getContainerSize()).also { result ->
            repeat(getContainerSize()) { slot -> result.add(getItem(slot)) }
        }

    override fun onOpen(who: CraftHumanEntity) {
        if (who !in viewers) viewers += who
    }

    override fun onClose(who: CraftHumanEntity) {
        viewers -= who
    }

    override fun getViewers(): List<HumanEntity> = viewers.toList()

    override fun getOwner(): InventoryHolder? {
        val tile = placedTile() ?: return null
        return bukkitHolder ?: BlockInventoryHolder(tile, craftInventory).also { bukkitHolder = it }
    }

    override fun getLocation(): org.bukkit.Location? = placedTile()?.location

    override fun getSlotsForFace(side: Direction): IntArray {
        val inventory = logicalInventory() ?: return IntArray(0)
        val face = side.toBukkitFace()
        return (0 until inventory.size).filter { slot ->
            inventory.definition.slot(slot).permitsInsertion(face) ||
                inventory.definition.slot(slot).permitsExtraction(face)
        }.toIntArray()
    }

    override fun canPlaceItemThroughFace(slot: Int, stack: NativeItemStack, side: Direction?): Boolean {
        val inventory = logicalInventory() ?: return false
        return inventory.canInsert(slot, stack.bukkit(), side?.toBukkitFace())
    }

    override fun canTakeItemThroughFace(slot: Int, stack: NativeItemStack, side: Direction): Boolean =
        logicalInventory()?.canExtract(slot, side.toBukkitFace()) == true

    private fun Direction.toBukkitFace(): org.bukkit.block.BlockFace =
        org.bukkit.block.BlockFace.valueOf(name)
}
