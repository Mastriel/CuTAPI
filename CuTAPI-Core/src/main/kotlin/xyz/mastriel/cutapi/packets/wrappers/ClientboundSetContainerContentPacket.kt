package xyz.mastriel.cutapi.packets.wrappers

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketContainer
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.packets.*

internal class ClientboundSetContainerContentPacket(handle: PacketContainer) : WrappedPacket(handle) {
    internal companion object : PacketInfo by packetInfo(PacketType.Play.Server.WINDOW_ITEMS)

    var windowId by intField(0)
    var items by itemListField(0)

    constructor(windowId: Int = 0, items: List<ItemStack?>) : this(PacketContainer(PacketType.Play.Server.WINDOW_ITEMS)) {
        this.windowId = windowId
        this.items = items
    }
}