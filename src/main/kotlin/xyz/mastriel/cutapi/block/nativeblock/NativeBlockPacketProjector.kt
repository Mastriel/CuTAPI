@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.level.block.Block
import xyz.mastriel.cutapi.nms.PacketEvent
import xyz.mastriel.cutapi.nms.PacketHandler
import xyz.mastriel.cutapi.nms.PacketListener

internal object NativeBlockPacketProjector : PacketListener {
    @PacketHandler
    fun blockEntityData(event: PacketEvent<ClientboundBlockEntityDataPacket>): ClientboundBlockEntityDataPacket? =
        event.packet.takeUnless { NativeBlockTypes.isCustomBlockEntityType(it.type) }

    @PacketHandler
    fun chunk(event: PacketEvent<ClientboundLevelChunkWithLightPacket>): ClientboundLevelChunkWithLightPacket {
        val packet = event.packet
        val data = packet.chunkData
        blockEntityInfo(data).removeIf { info ->
            NativeBlockTypes.isCustomBlockEntityType(blockEntityInfoType(info))
        }
        data.extraPackets.removeIf { packet ->
            packet is ClientboundBlockEntityDataPacket && NativeBlockTypes.isCustomBlockEntityType(packet.type)
        }

        return packet
    }

    @PacketHandler
    fun levelEvent(event: PacketEvent<ClientboundLevelEventPacket>): ClientboundLevelEventPacket {
        val packet = event.packet
        if (packet.type != DestroyBlockEvent) return packet
        val state = Block.stateById(packet.data)
        val projected = NativeBlockClientBridge.project(state)
        if (projected === state) return packet
        return ClientboundLevelEventPacket(packet.type, packet.pos, Block.getId(projected), packet.isGlobalEvent)
    }

    @PacketHandler
    fun particles(event: PacketEvent<ClientboundLevelParticlesPacket>): ClientboundLevelParticlesPacket {
        val packet = event.packet
        val option = packet.particle as? BlockParticleOption ?: return packet
        val projected = NativeBlockClientBridge.project(option.state)
        if (projected === option.state) return packet
        return ClientboundLevelParticlesPacket(
            BlockParticleOption(option.type, projected),
            packet.isOverrideLimiter,
            packet.alwaysShow(),
            packet.x,
            packet.y,
            packet.z,
            packet.xDist,
            packet.yDist,
            packet.zDist,
            packet.maxSpeed,
            packet.count,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun blockEntityInfo(data: ClientboundLevelChunkPacketData): MutableList<Any> {
        val field = ClientboundLevelChunkPacketData::class.java.getDeclaredField("blockEntitiesData")
            .apply { isAccessible = true }
        return field.get(data) as MutableList<Any>
    }

    private fun blockEntityInfoType(info: Any): net.minecraft.world.level.block.entity.BlockEntityType<*> {
        val field = info.javaClass.getDeclaredField("type").apply { isAccessible = true }
        return field.get(info) as net.minecraft.world.level.block.entity.BlockEntityType<*>
    }

    private const val DestroyBlockEvent: Int = 2001
}
