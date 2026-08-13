@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block

import org.bukkit.inventory.ItemType
import xyz.mastriel.cutapi.testing.MockBukkitTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockItemPolicyDefaultsTest : MockBukkitTest() {
    @Test
    fun `generated block items use melon backing and pig spawn egg projection`() {
        val policy = BlockItemPolicy.Generate()

        assertEquals(ItemType.GLISTERING_MELON_SLICE, policy.backingItem)
        assertEquals(ItemType.PIG_SPAWN_EGG, BlockItemPolicy.Generate.defaultClientItem())
    }
}
