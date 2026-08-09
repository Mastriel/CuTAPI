package xyz.mastriel.cutapi.item.nativeitem

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeItemChannelInitializerTest {
    @Test
    fun `resolves initial server codecs`() {
        assertEquals(
            NativeItemCodecNames(inbound = "decoder", outbound = "outbound_config"),
            resolveNativeItemCodecNames(
                listOf("timeout", "splitter", "decoder", "prepender", "outbound_config", "packet_handler"),
            ),
        )
    }

    @Test
    fun `resolves configured packet codecs`() {
        assertEquals(
            NativeItemCodecNames(inbound = "decoder", outbound = "encoder"),
            resolveNativeItemCodecNames(
                listOf("timeout", "splitter", "decoder", "prepender", "encoder", "packet_handler"),
            ),
        )
    }
}
