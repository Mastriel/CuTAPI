package xyz.mastriel.cutapi.nms

import org.bukkit.entity.*

@UsesNMS
internal data class PacketEvent<T: MojangPacket<*>>(val player: Player, val packet: T)