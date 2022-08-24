package xyz.mastriel.cutapi.packets

import org.bukkit.entity.Player

internal class PacketEvent <T: WrappedPacket> (val packet : T, val player: Player) {

    val isCancelled : Boolean = false


}