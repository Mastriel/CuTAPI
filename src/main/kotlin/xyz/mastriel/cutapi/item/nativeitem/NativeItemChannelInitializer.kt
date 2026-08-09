package xyz.mastriel.cutapi.item.nativeitem

import io.netty.channel.Channel
import io.papermc.paper.network.ChannelInitializeListener
import io.papermc.paper.network.ChannelInitializeListenerHolder
import net.kyori.adventure.key.Key

internal object NativeItemChannelInitializer : ChannelInitializeListener {
    private val key: Key = Key.key("cutapi", "native_item_codec")

    fun register() {
        if (!ChannelInitializeListenerHolder.hasListener(key)) {
            ChannelInitializeListenerHolder.addListener(key, this)
        }
    }

    fun unregister() {
        ChannelInitializeListenerHolder.removeListener(key)
    }

    override fun afterInitChannel(channel: Channel) {
        val pipeline = channel.pipeline()
        val codecs = resolveNativeItemCodecNames(pipeline.names())

        pipeline.addBefore(
            codecs.inbound,
            "cutapi_native_item_inbound_context",
            NativeItemNetworkContextHandler(inbound = true, outbound = false),
        )
        pipeline.addAfter(
            codecs.outbound,
            "cutapi_native_item_outbound_context",
            NativeItemNetworkContextHandler(inbound = false, outbound = true),
        )
    }
}

internal data class NativeItemCodecNames(val inbound: String, val outbound: String)

internal fun resolveNativeItemCodecNames(pipelineNames: Collection<String>): NativeItemCodecNames {
    val inbound = listOf("decoder", "inbound_config").firstOrNull(pipelineNames::contains)
        ?: error("No inbound Minecraft codec is present in $pipelineNames.")
    val outbound = listOf("encoder", "outbound_config").firstOrNull(pipelineNames::contains)
        ?: error("No outbound Minecraft codec is present in $pipelineNames.")
    return NativeItemCodecNames(inbound, outbound)
}
