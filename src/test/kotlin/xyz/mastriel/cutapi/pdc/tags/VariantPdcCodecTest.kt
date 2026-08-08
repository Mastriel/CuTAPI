package xyz.mastriel.cutapi.pdc.tags

import net.kyori.adventure.text.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class VariantPdcCodecTest : MockBukkitTest() {
    @Test
    public fun `mapped serializer values retain their canonical Variant PDC type`() {
        val player = server.addPlayer()
        val variant = ComponentSerializer.serialize(Component.text("hello")).getOrThrow()
        val encoded = VariantPdcCodec.encode(
            player.persistentDataContainer.adapterContext,
            variant
        )

        assertIs<Variant.String>(variant)
        assertEquals(
            VariantKind.String.id.toString(),
            encoded.get(
                id("cutapi:variant_type").toNamespacedKey(),
                PersistentDataType.STRING
            )
        )
        assertEquals(variant, VariantPdcCodec.decode(encoded))
    }
}
