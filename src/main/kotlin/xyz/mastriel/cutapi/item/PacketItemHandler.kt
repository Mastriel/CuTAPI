package xyz.mastriel.cutapi.item

import net.minecraft.network.HashedStack
import net.minecraft.network.HashedPatchMap
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket
import net.minecraft.world.item.Items
import xyz.mastriel.cutapi.item.nativeitem.CreativeSlotStackResolution
import xyz.mastriel.cutapi.item.nativeitem.NativeItemClientBridge
import xyz.mastriel.cutapi.nms.PacketEvent
import xyz.mastriel.cutapi.nms.PacketHandler
import xyz.mastriel.cutapi.nms.PacketListener
import xyz.mastriel.cutapi.nms.UsesNMS

/**
 * Keeps the server authoritative for hashed inventory clicks. Item identity projection itself is
 * handled centrally by the item stack stream codec bridge.
 */
@UsesNMS
internal object PacketItemHandler : PacketListener {
    @PacketHandler
    fun reconcileCreativeSlot(
        event: PacketEvent<ServerboundSetCreativeModeSlotPacket>,
    ): ServerboundSetCreativeModeSlotPacket? {
        return when (val resolution = NativeItemClientBridge.resolveCreativeSlotStack(event.packet.itemStack)) {
            is CreativeSlotStackResolution.Accept -> ServerboundSetCreativeModeSlotPacket(
                event.packet.slotNum.toInt(),
                resolution.stack,
            )
            CreativeSlotStackResolution.Reject -> null
        }
    }

    @PacketHandler
    fun reconcileInventoryClick(
        event: PacketEvent<ServerboundContainerClickPacket>,
    ): ServerboundContainerClickPacket {
        @Suppress("DEPRECATION")
        val deliberatelyMismatchedStack = HashedStack.ActualItem(
            Items.DIRT.builtInRegistryHolder(),
            -1,
            HashedPatchMap(emptyMap(), emptySet()),
        )

        return ServerboundContainerClickPacket(
            event.packet.containerId,
            event.packet.stateId,
            event.packet.slotNum,
            event.packet.buttonNum,
            event.packet.clickType,
            event.packet.changedSlots,
            deliberatelyMismatchedStack,
        )
    }
}
