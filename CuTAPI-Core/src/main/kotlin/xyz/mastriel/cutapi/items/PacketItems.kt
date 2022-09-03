package xyz.mastriel.cutapi.items

import org.bukkit.event.EventPriority
import xyz.mastriel.cutapi.items.CuTItemStack.Companion.isCustom
import xyz.mastriel.cutapi.packets.PacketEvent
import xyz.mastriel.cutapi.packets.PacketHandler
import xyz.mastriel.cutapi.packets.PacketListener
import xyz.mastriel.cutapi.packets.wrappers.ClientboundSetSlotPacket


// Handles server/client differences in items.
internal object PacketItems : PacketListener {

    @PacketHandler(EventPriority.MONITOR)
    fun itemSlotChangePacketEvent(event: PacketEvent<ClientboundSetSlotPacket>) {
        if (!event.packet.itemStack.isCustom) return
        val itemStack = event.packet.itemStack.clone()

        val customItem = CuTItemStack(itemStack)
        itemStack.editMeta {
            it.lore(customItem.getLore(event.player))
        }
        event.packet.itemStack = itemStack
    }
}