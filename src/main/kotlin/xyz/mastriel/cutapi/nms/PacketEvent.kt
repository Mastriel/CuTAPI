package xyz.mastriel.cutapi.nms

import org.bukkit.entity.Player

@UsesNMS
internal data class PacketEvent<T: MojangPacket<*>>(val player: Player, val packet: T)