package xyz.mastriel.cutapi.packets

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.Plugin

internal open class WrappedPacket(val handle: PacketContainer) {

    val packetType get() = handle.type

    fun sendPacket(target: Player): Boolean {
        try {
            val manager = ProtocolLibrary.getProtocolManager()
            manager.sendServerPacket(target, handle)
            return true
        } catch (ex: Exception) {
            Plugin.error("Cannot send packet to ${target.name}: $ex")
            return false
        }
    }

    fun broadcastPacket(): Boolean {
        try {
            val manager = ProtocolLibrary.getProtocolManager()
            manager.broadcastServerPacket(handle)
            return true
        } catch (ex: Exception) {
            Plugin.error("Cannot broadcast packet: $ex")
            return false
        }
    }

    fun recievePacket(sender: Player): Boolean {
        try {
            val manager = ProtocolLibrary.getProtocolManager()
            manager.receiveClientPacket(sender, handle)
            return true
        } catch (ex: Exception) {
            Plugin.error("Cannot simulate a recieve packet from ${sender.name}: $ex")
            return false
        }
    }




}