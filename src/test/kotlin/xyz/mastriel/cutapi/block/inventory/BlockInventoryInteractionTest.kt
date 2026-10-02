package xyz.mastriel.cutapi.block.inventory

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.block.BlockRuntimeEvents
import xyz.mastriel.cutapi.item.ItemSystemEvents
import xyz.mastriel.cutapi.testing.MockBukkitTest
import kotlin.test.*

public class BlockInventoryInteractionTest : MockBukkitTest() {
    @Test
    public fun `opening with a held block consumes both block and item use`() {
        val event = interaction()
        var opens = 0
        event.handleBlockInventoryInteraction { opens++ }

        assertEquals(1, opens)
        assertEquals(Event.Result.DENY, event.useInteractedBlock())
        assertEquals(Event.Result.DENY, event.useItemInHand())
    }

    @Test
    public fun `sneaking leaves the click available for placement`() {
        val event = interaction()
        event.player.isSneaking = true
        event.handleBlockInventoryInteraction { fail("Sneaking must not open a GUI") }

        assertEquals(Event.Result.ALLOW, event.useInteractedBlock())
        assertEquals(Event.Result.ALLOW, event.useItemInHand())
    }

    @Test
    public fun `denied block use prevents opening without changing item permission`() {
        val event = interaction()
        event.setUseInteractedBlock(Event.Result.DENY)
        event.handleBlockInventoryInteraction { fail("Denied block use must not open a GUI") }

        assertEquals(Event.Result.ALLOW, event.useItemInHand())
    }

    @Test
    public fun `denied item use still allows an inventory to open`() {
        val event = interaction()
        event.setUseItemInHand(Event.Result.DENY)
        var opened = false
        event.handleBlockInventoryInteraction { opened = true }

        assertTrue(opened)
    }

    @Test
    public fun `off hand cannot reopen the GUI or use its held item`() {
        val event = interaction(EquipmentSlot.OFF_HAND)
        event.handleBlockInventoryInteraction { fail("Off hand must not open a second GUI") }

        assertEquals(Event.Result.DENY, event.useInteractedBlock())
        assertEquals(Event.Result.DENY, event.useItemInHand())
    }

    @Test
    public fun `a rejected opening cannot fall back to using the held item`() {
        val event = interaction()
        event.handleBlockInventoryInteraction { /* GUI open event rejected the opening. */ }

        assertEquals(Event.Result.DENY, event.useItemInHand())
    }

    @Test
    public fun `block interactions run before custom item placement`() {
        val blockPriority = BlockRuntimeEvents::class.java
            .getMethod("onInteract", PlayerInteractEvent::class.java)
            .getAnnotation(EventHandler::class.java).priority
        val itemPriority = ItemSystemEvents::class.java
            .getMethod("onInteract", PlayerInteractEvent::class.java)
            .getAnnotation(EventHandler::class.java).priority

        assertTrue(blockPriority.slot < itemPriority.slot)
    }

    private fun interaction(hand: EquipmentSlot = EquipmentSlot.HAND): PlayerInteractEvent {
        val player = server.addPlayer()
        val block = server.addSimpleWorld("world").getBlockAt(0, 64, 0)
        block.type = Material.STONE
        return PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, ItemStack(Material.STONE), block, BlockFace.UP, hand,
        ).also {
            it.setUseInteractedBlock(Event.Result.ALLOW)
            it.setUseItemInHand(Event.Result.ALLOW)
        }
    }
}
