@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item.attachments

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.ItemRenderContext
import xyz.mastriel.cutapi.testing.MockBukkitTest
import kotlin.test.Test
import kotlin.test.assertEquals

public class DisplayAsTest : MockBukkitTest() {
    @Test
    public fun `stackable items retain their limit when displayed as potions`() {
        verifyStackLimit(ItemStack(Material.GLISTERING_MELON_SLICE, 32), ItemType.POTION, 64)
    }

    @Test
    public fun `unstackable items remain unstackable when displayed as stackable items`() {
        verifyStackLimit(ItemStack(Material.POTION), ItemType.GLISTERING_MELON_SLICE, 1)
    }

    @Test
    public fun `explicit stack limits survive a change in appearance`() {
        val original = ItemStack(Material.GLISTERING_MELON_SLICE, 8)
        original.editMeta { meta -> meta.setMaxStackSize(16) }
        verifyStackLimit(original, ItemType.POTION, 16)
    }

    private fun verifyStackLimit(original: ItemStack, displayedType: ItemType, expectedLimit: Int) {
        val before = original.clone()
        val rendered = original.clone()
        val appearance = DisplayAs(displayedType)
        DisplayAsSystem.onRender(
            ItemRenderContext(
                DisplayAsTestStack(rendered, appearance),
                DisplayAsTestStack(original, appearance),
                viewer = null,
            ),
        )

        assertEquals(displayedType.asMaterial(), rendered.type)
        assertEquals(before.amount, rendered.amount)
        assertEquals(expectedLimit, rendered.maxStackSize)
        assertEquals(before, original)
    }
}

private class DisplayAsTestStack(handle: ItemStack, private val appearance: DisplayAs) : CuTItemStack(handle) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ItemAttachment> getAttachment(schema: Schema<T>): T {
        require(schema === DisplayAs)
        return appearance as T
    }
}
