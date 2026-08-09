package xyz.mastriel.cutapi.item.nativeitem

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.util.AttributeKey
import org.bukkit.entity.Player

internal enum class NetworkDirection { Clientbound, Serverbound }

internal data class NativeItemNetworkCall(val direction: NetworkDirection, val player: Player?)

internal object NativeItemNetworkContext {
    private val current = ThreadLocal<NativeItemNetworkCall?>()

    fun current(): NativeItemNetworkCall? = current.get()

    fun <T> with(call: NativeItemNetworkCall, block: () -> T): T {
        val previous = current()
        current.set(call)
        return try {
            block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }
}

internal val NetworkPlayerKey: AttributeKey<Player> = AttributeKey.valueOf("cutapi:native_item_player")

internal class NativeItemNetworkContextHandler(
    private val inbound: Boolean,
    private val outbound: Boolean,
) : ChannelDuplexHandler() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (!inbound) return super.channelRead(ctx, msg)
        NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Serverbound, ctx.channel().attr(NetworkPlayerKey).get()),
        ) { super.channelRead(ctx, msg) }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (!outbound) return super.write(ctx, msg, promise)
        NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Clientbound, ctx.channel().attr(NetworkPlayerKey).get()),
        ) { super.write(ctx, msg, promise) }
    }
}
