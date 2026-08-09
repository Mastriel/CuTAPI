@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.nativeblock

import io.papermc.paper.event.packet.PlayerChunkLoadEvent
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.phys.Vec3
import org.bukkit.Chunk
import org.bukkit.block.Block
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import xyz.mastriel.cutapi.nms.sendTo
import java.util.UUID

internal object NativeBlockDisplayManager : Listener {
    private data class TrackedDisplay(val entityId: Int, val state: net.minecraft.world.level.block.state.BlockState)

    private val displays: MutableMap<UUID, MutableMap<Long, TrackedDisplay>> = mutableMapOf()

    @EventHandler
    fun onChunkLoad(event: PlayerChunkLoadEvent) {
        scan(event.player, event.chunk)
    }

    @EventHandler
    fun onChunkUnload(event: PlayerChunkUnloadEvent) {
        val tracked = displays[event.player.uniqueId] ?: return
        val removed = tracked.entries.filter { (packed, _) ->
            val pos = BlockPos.of(packed)
            (pos.x shr 4) == event.chunk.x && (pos.z shr 4) == event.chunk.z
        }
        if (removed.isEmpty()) return
        removed.forEach { tracked.remove(it.key) }
        ClientboundRemoveEntitiesPacket(*removed.map { it.value.entityId }.toIntArray()).sendTo(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        displays.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        // A dimension transition clears client entities; discard server tracking so the new world's
        // chunk events can allocate displays even when packed block positions overlap.
        displays.remove(event.player.uniqueId)
    }

    fun refresh(block: Block) {
        block.chunk.playersSeeingChunk.forEach { refresh(it, block) }
    }

    private fun scan(player: Player, chunk: Chunk) {
        val handle = (chunk as CraftChunk).getHandle(net.minecraft.world.level.chunk.status.ChunkStatus.FULL)
        handle.sections.forEachIndexed { sectionIndex, section ->
            if (!section.maybeHas { NativeBlockClientBridge.resolve(it)?.displayEntity != null }) return@forEachIndexed
            val sectionY = handle.getSectionYFromSectionIndex(sectionIndex) shl 4
            for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                val state = section.getBlockState(x, y, z)
                if (NativeBlockClientBridge.resolve(state)?.displayEntity == null) continue
                refresh(player, chunk.getBlock(x, sectionY + y, z))
            }
        }
    }

    private fun refresh(player: Player, block: Block) {
        val level = (block.world as CraftWorld).handle
        val pos = BlockPos(block.x, block.y, block.z)
        val state = level.getBlockState(pos)
        val display = NativeBlockClientBridge.resolve(state)?.displayEntity
        val tracked = displays.getOrPut(player.uniqueId, ::mutableMapOf)
        val previous = tracked[pos.asLong()]
        if (previous != null && previous.state === state && display != null) return
        if (previous != null) {
            ClientboundRemoveEntitiesPacket(previous.entityId).sendTo(player)
            tracked.remove(pos.asLong())
        }
        if (display == null) return

        val entity = Display.ItemDisplay(EntityType.ITEM_DISPLAY, level).apply {
            setPos(block.x + 0.5, block.y + 0.5, block.z + 0.5)
            itemStack = CraftItemStack.asNMSCopy(display.item)
            itemTransform = ItemDisplayContext.valueOf(display.transform.name)
        }
        ClientboundAddEntityPacket(
            entity.id,
            entity.uuid,
            entity.x,
            entity.y,
            entity.z,
            0.0f,
            0.0f,
            EntityType.ITEM_DISPLAY,
            0,
            Vec3.ZERO,
            0.0,
        ).sendTo(player)
        ClientboundSetEntityDataPacket(entity.id, entity.entityData.nonDefaultValues.orEmpty()).sendTo(player)
        tracked[pos.asLong()] = TrackedDisplay(entity.id, state)
    }
}
