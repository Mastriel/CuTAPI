package xyz.mastriel.cutapi.packets

import com.comphenix.protocol.PacketType

internal interface PacketInfo {
    val packetType: PacketType
}


internal fun packetInfo(packetType: PacketType) : PacketInfo {
    return object : PacketInfo {
        override val packetType: PacketType = packetType
    }
}