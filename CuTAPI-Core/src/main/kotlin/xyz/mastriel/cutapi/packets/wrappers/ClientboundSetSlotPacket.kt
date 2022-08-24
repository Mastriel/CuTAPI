package xyz.mastriel.cutapi.packets.wrappers

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketContainer
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.packets.*

internal class ClientboundSetSlotPacket(handle: PacketContainer) : WrappedPacket(handle) {
    internal companion object : PacketInfo by packetInfo(PacketType.Play.Server.SET_SLOT)

    var windowId by intField(0)
    var slotIndex by intField(1)
    var itemStack by itemField(0)

    constructor(slotIndex: Int, windowId: Int, itemStack: ItemStack) : this(PacketContainer(PacketType.Play.Server.SET_SLOT)) {
        this.slotIndex = slotIndex
        this.windowId = windowId
        this.itemStack = itemStack
    }
}