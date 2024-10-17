package xyz.mastriel.cutapi.nms

import io.netty.channel.*
import net.minecraft.network.protocol.game.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.*


@UsesNMS
public class PacketEventHandler(public val player: Player) : ChannelDuplexHandler() {

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
        try {
            val packet = msg as? MojangPacket<*> ?: return

            val event = PacketEvent(player, packet)

            val newPacket = CuTAPI.packetEventManager.trigger(event)

            if (newPacket != null) super.channelRead(ctx, newPacket)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}