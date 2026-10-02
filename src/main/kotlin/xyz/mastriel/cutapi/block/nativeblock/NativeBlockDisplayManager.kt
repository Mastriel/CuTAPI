@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.nativeblock

import io.papermc.paper.event.packet.PlayerChunkLoadEvent
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent
import com.mojang.math.Transformation
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Brightness
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.LightLayer
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
import org.joml.Quaternionf
import org.joml.Vector3f
import xyz.mastriel.cutapi.block.VirtualItemDisplayDefinition
import xyz.mastriel.cutapi.nms.sendTo
import java.util.UUID

internal object NativeBlockDisplayManager : Listener {
    private data class DisplayBlockKey(val worldId: UUID, val position: Long)

    private data class DisplayLight(val block: Int, val sky: Int) {
        fun toNative(): Brightness = Brightness(block, sky)
    }

    private data class TrackedDisplay(
        val entity: Display.ItemDisplay,
        val state: net.minecraft.world.level.block.state.BlockState,
        val light: DisplayLight,
        var breakingStage: Int,
    )

    private val displays: MutableMap<UUID, MutableMap<Long, TrackedDisplay>> = mutableMapOf()
    private val breakingStages: MutableMap<DisplayBlockKey, MutableMap<Int, Int>> = mutableMapOf()

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
        ClientboundRemoveEntitiesPacket(*removed.map { it.value.entity.id }.toIntArray()).sendTo(event.player)
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

    fun setBreakingStage(block: Block, breakerId: Int, stage: Int) {
        val pos = BlockPos(block.x, block.y, block.z)
        val key = DisplayBlockKey(block.world.uid, pos.asLong())
        val stages = breakingStages.getOrPut(key, ::mutableMapOf)
        if (stage in 0..9) stages[breakerId] = stage else stages.remove(breakerId)
        if (stages.isEmpty()) breakingStages.remove(key)
        val effectiveStage = stages.values.maxOrNull() ?: -1

        val center = block.location.toCenterLocation()
        block.chunk.playersSeeingChunk.forEach { viewer ->
            val tracked = displays[viewer.uniqueId]?.get(pos.asLong()) ?: return@forEach
            val display = NativeBlockClientBridge.resolve(tracked.state)?.displayEntity ?: return@forEach
            val viewerStage = effectiveStage.takeIf {
                viewer.location.distanceSquared(center) <= 32.0 * 32.0
            } ?: -1
            updateBreakingStage(viewer, tracked, display, viewerStage)
        }
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
        if (display == null) {
            if (previous != null) {
                ClientboundRemoveEntitiesPacket(previous.entity.id).sendTo(player)
                tracked.remove(pos.asLong())
            }
            return
        }

        val light = display.brightness?.toDisplayLight() ?: carrierLight(level, pos)
        val breakingStage = currentBreakingStage(block.world.uid, pos).takeIf {
            player.location.distanceSquared(block.location.toCenterLocation()) <= 32.0 * 32.0
        } ?: -1
        if (
            previous != null && previous.state === state && previous.light == light &&
            previous.breakingStage == breakingStage
        ) return
        // Model, orientation, and lighting changes keep the client entity alive. Removing and
        // respawning it lets the client render a frame with no block visual between packets.
        val entity = previous?.entity ?: Display.ItemDisplay(EntityType.ITEM_DISPLAY, level).apply {
            setPos(block.x + 0.5, block.y + 0.5, block.z + 0.5)
        }
        entity.itemStack = CraftItemStack.asNMSCopy(display.itemForBreakingStage(breakingStage))
        entity.itemTransform = ItemDisplayContext.valueOf(display.transform.name)
        entity.setBrightnessOverride(light.toNative())
        entity.setTransformation(
            Transformation(
                Vector3f(),
                Quaternionf().rotateY(Math.toRadians(-display.yRotationDegrees.toDouble()).toFloat()),
                Vector3f(1.0f, 1.0f, 1.0f),
                Quaternionf(),
            ),
        )
        if (previous == null) {
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
        }
        entity.sendChangesTo(player)
        tracked[pos.asLong()] = TrackedDisplay(entity, state, light, breakingStage)
    }

    /**
     * Samples light from the carrier's faces as well as its own cell. The authoritative native block
     * can occlude its cell even when the projected carrier does not, so the face probes represent the
     * light that reaches the client-side carrier more faithfully than the center value alone.
     */
    private fun carrierLight(level: net.minecraft.server.level.ServerLevel, pos: BlockPos): DisplayLight {
        var blockLight = level.getBrightness(LightLayer.BLOCK, pos)
        var skyLight = level.getBrightness(LightLayer.SKY, pos)
        for (direction in Direction.entries) {
            val face = pos.relative(direction)
            blockLight = maxOf(blockLight, level.getBrightness(LightLayer.BLOCK, face))
            skyLight = maxOf(skyLight, level.getBrightness(LightLayer.SKY, face))
        }
        return DisplayLight(blockLight, skyLight)
    }

    private fun org.bukkit.entity.Display.Brightness.toDisplayLight(): DisplayLight =
        DisplayLight(blockLight, skyLight)

    private fun currentBreakingStage(worldId: UUID, pos: BlockPos): Int =
        breakingStages[DisplayBlockKey(worldId, pos.asLong())]?.values?.maxOrNull() ?: -1

    private fun updateBreakingStage(
        player: Player,
        tracked: TrackedDisplay,
        display: VirtualItemDisplayDefinition,
        stage: Int,
    ) {
        if (tracked.breakingStage == stage) return
        tracked.breakingStage = stage
        tracked.entity.itemStack = CraftItemStack.asNMSCopy(display.itemForBreakingStage(stage))
        tracked.entity.sendChangesTo(player)
    }

    private fun Display.ItemDisplay.sendChangesTo(player: Player) {
        // Dirty entries include values reset to their defaults (for example, rotation and NONE
        // item transform); nonDefaultValues would leave the client's previous values in place.
        val changes = entityData.packDirty() ?: return
        ClientboundSetEntityDataPacket(id, changes).sendTo(player)
    }

    private fun VirtualItemDisplayDefinition.itemForBreakingStage(
        stage: Int,
    ): org.bukkit.inventory.ItemStack {
        val model = breakingItemModels.getOrNull(stage) ?: return item
        return item.clone().apply {
            editMeta { meta -> meta.itemModel = model }
        }
    }
}
