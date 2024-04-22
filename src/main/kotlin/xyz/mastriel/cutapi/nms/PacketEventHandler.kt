package xyz.mastriel.cutapi.nms

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.CuTAPI


@UsesNMS
internal class PacketEventHandler(val player : Player) : ChannelDuplexHandler() {

    // outgoing packets (clientbound)
    override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {

        val packet = msg as? MojangPacket<*> ?: return super.write(ctx, msg, promise)

        if (packet is ClientboundBundlePacket) {
            val packets = packet.subPackets().mapNotNull {
                CuTAPI.packetEventManager.trigger(PacketEvent(player, it))
            }
            if (packets.isEmpty()) return
            super.write(ctx, ClientboundBundlePacket(packets), promise)
        } else {
            val event = PacketEvent(player, packet)
            val newPacket = CuTAPI.packetEventManager.trigger(event)

            if (newPacket != null) super.write(ctx, newPacket, promise)
        }
    }

    // incoming packets (serverbound)
    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val packet = msg as MojangPacket<*>

        val event = PacketEvent(player, packet)

        val newPacket = CuTAPI.packetEventManager.trigger(event)

        if (newPacket != null) super.channelRead(ctx, newPacket)
    }
}