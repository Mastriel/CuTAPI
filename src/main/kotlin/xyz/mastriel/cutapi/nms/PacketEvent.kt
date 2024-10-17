package xyz.mastriel.cutapi.nms

import org.bukkit.entity.*

@UsesNMS
public data class PacketEvent<T : MojangPacket<*>>(val player: Player, val packet: T)