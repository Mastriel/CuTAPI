@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.gui.internal

import io.papermc.paper.adventure.PaperAdventure
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.inventory.MenuType
import xyz.mastriel.cutapi.gui.GuiBaseContainerOverlay
import xyz.mastriel.cutapi.nms.PacketEvent
import xyz.mastriel.cutapi.nms.PacketHandler
import xyz.mastriel.cutapi.nms.PacketListener

internal object GuiBaseContainerPacketListener : PacketListener {
    @PacketHandler
    fun openScreen(event: PacketEvent<ClientboundOpenScreenPacket>): ClientboundOpenScreenPacket {
        val packet = event.packet
        val rows = when (packet.type) {
            MenuType.GENERIC_9x1 -> 1
            MenuType.GENERIC_9x2 -> 2
            MenuType.GENERIC_9x3 -> 3
            MenuType.GENERIC_9x4 -> 4
            MenuType.GENERIC_9x5 -> 5
            MenuType.GENERIC_9x6 -> 6
            else -> return packet
        }
        val title = PaperAdventure.asAdventure(packet.title)
        val decorated = GuiBaseContainerOverlay.compose(rows, title)
        if (decorated === title) return packet
        return ClientboundOpenScreenPacket(packet.containerId, packet.type, PaperAdventure.asVanilla(decorated))
    }
}
