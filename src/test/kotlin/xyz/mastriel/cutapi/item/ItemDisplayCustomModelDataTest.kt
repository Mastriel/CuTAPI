package xyz.mastriel.cutapi.item

import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.Color as BukkitColor
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.data.SCHEMA_TYPE_DISCRIMINATOR
import xyz.mastriel.cutapi.data.Variant
import xyz.mastriel.cutapi.data.getOrThrow
import xyz.mastriel.cutapi.testing.MockBukkitTest
import xyz.mastriel.cutapi.utils.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class ItemDisplayCustomModelDataTest : MockBukkitTest() {
    @Test
    public fun `builder preserves the independent insertion order of every value kind`() {
        val component = ItemDisplayCustomModelDataBuilder().apply {
            float(3f)
            float(7f)
            flag(true)
            flag(false)
            string("first")
            string("second")
            color(Color.of(0x123456))
            color(Color.of(0xABCDEF))
        }.build()

        assertEquals(listOf(3f, 7f), component.floats())
        assertEquals(listOf(true, false), component.flags())
        assertEquals(listOf("first", "second"), component.strings())
        assertEquals(
            listOf(BukkitColor.fromRGB(0x123456), BukkitColor.fromRGB(0xABCDEF)),
            component.colors(),
        )
    }

    @Test
    public fun `display custom model data changes only the detached rendered stack`() {
        val source = ItemStack(Material.PAPER)
        val rendered = source.clone()
        val display = ItemDisplayBuilder(TestCuTItemStack(source), viewer = null).apply {
            customModelData {
                float(12f)
                flag(false)
                color(Color.of(0x7F7F7F))
                color(Color.of(0xC6C6C6))
            }
        }

        display.applyCustomModelDataTo(rendered)

        assertFalse(source.hasData(DataComponentTypes.CUSTOM_MODEL_DATA))
        assertTrue(rendered.hasData(DataComponentTypes.CUSTOM_MODEL_DATA))
        val component = rendered.getData(DataComponentTypes.CUSTOM_MODEL_DATA)!!
        assertEquals(listOf(12f), component.floats())
        assertEquals(listOf(false), component.flags())
        assertEquals(
            listOf(BukkitColor.fromRGB(0x7F7F7F), BukkitColor.fromRGB(0xC6C6C6)),
            component.colors(),
        )
    }

    @Test
    public fun `progress visual reads legacy data with new presentation defaults`() {
        val legacy = Variant.Map(
            linkedMapOf(
                SCHEMA_TYPE_DISCRIMINATOR to Variant.String("cutapi:progress_visual"),
                "progress" to Variant.Float(0.5f),
            )
        )

        val visual = CustomItem.ProgressVisual.deserialize(legacy).getOrThrow()

        assertEquals(0.5f, visual.progress)
        assertEquals(Color.of(0x8B8B8B), visual.unfilledColor)
        assertEquals(Color.of(0xFFFFFF), visual.filledColor)
        assertTrue(visual.inventoryBackground)
        assertEquals(legacy, CustomItem.ProgressVisual.serialize(visual).getOrThrow())
    }
}

private class TestCuTItemStack(handle: ItemStack) : CuTItemStack(handle)
