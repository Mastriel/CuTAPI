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

    @Test
    public fun `maps store values directly under encoded string keys`() {
        val player = server.addPlayer()
        val variant = Variant.Map(
            linkedMapOf(
                "" to Variant.Null,
                SCHEMA_TYPE_DISCRIMINATOR to Variant.String("cutapi:test"),
                "Upper/ümlaut: key" to Variant.List(listOf(Variant.Int(3)))
            )
        )

        val encoded = VariantPdcCodec.encode(
            player.persistentDataContainer.adapterContext,
            variant
        )
        val mapKeys = encoded.keys.filter { it.namespace == "cutapi" && it.key.startsWith("variant_map/") }

        assertEquals(variant.size, mapKeys.size)
        assertFalse(encoded.has(id("cutapi:variant_size").toNamespacedKey()))
        assertNotNull(
            encoded.get(
                id("cutapi:variant_map/2474797065").toNamespacedKey(),
                PersistentDataType.TAG_CONTAINER
            )
        )
        assertEquals(variant, VariantPdcCodec.decode(encoded))
    }

    @Test
    public fun `legacy indexed maps require migration`() {
        val player = server.addPlayer()
        val encoded = player.persistentDataContainer.adapterContext.newPersistentDataContainer()
        encoded.set(
            id("cutapi:variant_type").toNamespacedKey(),
            PersistentDataType.STRING,
            VariantKind.Map.id.toString()
        )
        encoded.set(
            id("cutapi:variant_size").toNamespacedKey(),
            PersistentDataType.INTEGER,
            1
        )

        val error = assertFailsWith<DataSerializationException> {
            VariantPdcCodec.decode(encoded)
        }

        assertContains(error.message.orEmpty(), "must be migrated")
    }
}
