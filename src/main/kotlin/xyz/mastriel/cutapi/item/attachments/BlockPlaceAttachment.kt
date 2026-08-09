@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.item.attachments

import net.minecraft.core.BlockPos
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.block.nativeblock.*
import xyz.mastriel.cutapi.nms.nms
import org.bukkit.GameMode
import org.bukkit.SoundCategory
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockCanBuildEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.Collections
import java.util.IdentityHashMap

public data class BlockPlaceAttachment(
    public val tileId: Identifier,
    public val consumesItem: Boolean = true
) : ItemAttachment {
    public constructor(
        tile: CustomTile<*>,
        consumesItem: Boolean = true
    ) : this(tile.id, consumesItem)

    public val tile: CustomTile<*> get() = CustomTile.get(tileId)

    public companion object : Schema<BlockPlaceAttachment> by schema(id("cutapi:block_place"), {
        property(BlockPlaceAttachment::tileId, VariantSerializer.Id, name = "tile_id")
        property(BlockPlaceAttachment::consumesItem, VariantSerializer.Boolean, name = "consumes_item")
    })
}

internal object BlockPlaceSystem : ItemSystem by attachmentItemSystem(BlockPlaceAttachment) {
    private val syntheticEvents: MutableSet<BlockPlaceEvent> =
        Collections.newSetFromMap(IdentityHashMap())

    override fun onRightClick(context: ItemInteractContext) {
        val interact = context.event
        if (interact.action != Action.RIGHT_CLICK_BLOCK || interact.hand != EquipmentSlot.HAND) return
        if (interact.isCancelled || interact.useItemInHand() == Event.Result.DENY) return
        val clicked = interact.clickedBlock ?: return
        if (!context.player.isSneaking && clicked.type.isInteractable) return
        val target = if (clicked.isReplaceable) clicked else clicked.getRelative(interact.blockFace)
        if (!target.isReplaceable) return

        val attachment = context.item.getAttachment(BlockPlaceAttachment)
        val definition = attachment.tile
        val nmsPlayer = context.player.nms()
        val level = nmsPlayer.level()
        val pos = BlockPos(target.x, target.y, target.z)
        val direction = net.minecraft.core.Direction.valueOf(interact.blockFace.name)
        if (level.isOutsideBuildHeight(pos) || !level.worldBorder.isWithinBounds(pos)) return
        if (!nmsPlayer.mayInteract(level, pos) || !nmsPlayer.mayUseItemAt(pos, direction, nmsPlayer.mainHandItem)) return

        val nativeState = NativeBlockTypes.state(definition, definition.descriptor.states.defaultState)
        val projectedData = NativeBlockClientBridge.project(nativeState).createCraftBlockData()
        val canBuild = BlockCanBuildEvent(
            target,
            context.player,
            projectedData,
            target.canPlace(projectedData),
            EquipmentSlot.HAND,
        )
        Plugin.server.pluginManager.callEvent(canBuild)
        if (!canBuild.isBuildable) return

        val replaced = target.state
        val placed = NativeBlockTypes.setAt(target, definition, definition.descriptor.states.defaultState)
        val place = BlockPlaceEvent(
            target,
            replaced,
            clicked,
            context.item.vanilla().clone(),
            context.player,
            true,
            EquipmentSlot.HAND,
        )
        syntheticEvents += place
        try {
            Plugin.server.pluginManager.callEvent(place)
        } finally {
            syntheticEvents -= place
        }
        interact.setUseItemInHand(Event.Result.DENY)
        if (place.isCancelled || !place.canBuild()) {
            replaced.update(true, false)
            NativeBlockDisplayManager.refresh(target)
            return
        }

        BlockSystem.dispatch(placed) {
            it.onPlaced(BlockPlaceContext(placed, context.player, place))
        }
        if (attachment.consumesItem && context.player.gameMode != GameMode.CREATIVE) {
            context.item.handle.amount = (context.item.handle.amount - 1).coerceAtLeast(0)
        }
        val sounds = target.blockSoundGroup
        target.world.playSound(
            target.location,
            sounds.placeSound,
            SoundCategory.BLOCKS,
            (sounds.volume + 1.0f) / 2.0f,
            sounds.pitch * 0.8f,
        )
    }

    override fun onPlace(context: ItemBlockPlaceContext) {
        if (context.event in syntheticEvents) return
        if (context.event.isCancelled || !context.event.canBuild()) return
        for (attachment in context.item.getAttachments(BlockPlaceAttachment)) {
            attachment.tile.setAt(context.event.blockPlaced)
            val placed = NativeBlockTypes.placedTile(context.event.blockPlaced)
            BlockSystem.dispatch(placed) {
                it.onPlaced(BlockPlaceContext(placed, context.player, context.event))
            }
            if (!attachment.consumesItem) context.item.handle.amount += 1
        }
    }
}
