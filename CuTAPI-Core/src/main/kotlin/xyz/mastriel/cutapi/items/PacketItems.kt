package xyz.mastriel.cutapi.items

import org.bukkit.Material
import org.bukkit.event.EventPriority
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.packets.PacketEvent
import xyz.mastriel.cutapi.packets.PacketHandler
import xyz.mastriel.cutapi.packets.PacketListener
import xyz.mastriel.cutapi.packets.wrappers.ClientboundSetSlotPacket
import xyz.mastriel.cutapi.utils.colored


// Handles server/client differences in items.
internal object PacketItems : PacketListener {

    @PacketHandler(EventPriority.MONITOR)
    fun itemSlotChangePacketEvent(event: PacketEvent<ClientboundSetSlotPacket>) {
        val itemStack = event.packet.itemStack
        itemStack.editMeta {
            it.lore(listOf("&f&lCOMMON".colored))
        }
    }

    @PacketHandler(EventPriority.NORMAL)
    fun randomizeAllItems(event: PacketEvent<ClientboundSetSlotPacket>) {
        val amount = event.packet.itemStack.amount
        val newItem = Material.values()
            .filter(Material::isItem)
            .random()
            .let(::ItemStack)
            .asQuantity(amount)
        event.packet.itemStack = newItem
    }
}