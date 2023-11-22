package xyz.mastriel.cutapi.packets.wrappers

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketContainer
import xyz.mastriel.cutapi.packets.PacketInfo
import xyz.mastriel.cutapi.packets.WrappedPacket
import xyz.mastriel.cutapi.packets.intField
import xyz.mastriel.cutapi.packets.packetInfo

internal class ServerboundCloseContainerPacket(handle: PacketContainer) : WrappedPacket(handle) {
    internal companion object : PacketInfo by packetInfo(PacketType.Play.Client.CLOSE_WINDOW)

    var windowId by intField(0)

    constructor(windowId: Int = 0) : this(PacketContainer(PacketType.Play.Client.CLOSE_WINDOW)) {
        this.windowId = windowId
    }
}