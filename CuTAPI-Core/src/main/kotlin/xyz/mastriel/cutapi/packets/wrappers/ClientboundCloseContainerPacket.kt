package xyz.mastriel.cutapi.packets.wrappers

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketContainer
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.packets.*
import xyz.mastriel.cutapi.packets.PacketInfo
import xyz.mastriel.cutapi.packets.WrappedPacket
import xyz.mastriel.cutapi.packets.intField
import xyz.mastriel.cutapi.packets.itemListField
import xyz.mastriel.cutapi.packets.packetInfo

internal class ClientboundCloseContainerPacket(handle: PacketContainer) : WrappedPacket(handle) {
    internal companion object : PacketInfo by packetInfo(PacketType.Play.Server.CLOSE_WINDOW)

    var windowId by intField(0)

    constructor(windowId: Int = 0) : this(PacketContainer(PacketType.Play.Server.CLOSE_WINDOW)) {
        this.windowId = windowId
    }
}