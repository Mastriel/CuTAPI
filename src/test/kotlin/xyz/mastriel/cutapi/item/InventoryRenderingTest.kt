package xyz.mastriel.cutapi.item

import org.bukkit.Material
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockbukkit.mockbukkit.inventory.InventoryMock
import xyz.mastriel.cutapi.testing.MockBukkitTest
import kotlin.test.Test
import kotlin.test.assertEquals

public class InventoryRenderingTest : MockBukkitTest() {
    @Test
    public fun `force rerender refreshes every distinct player viewer without changing contents`() {
        val first = TrackingPlayer(server, "first")
        val second = TrackingPlayer(server, "second")
        server.addPlayer(first)
        server.addPlayer(second)
        val inventory = InventoryMock(null, 9, InventoryType.CHEST)
        val stack = ItemStack(Material.DIAMOND, 3)
        inventory.setItem(0, stack)
        inventory.addViewers(first, first, second)

        inventory.forceRerender()

        assertEquals(1, first.inventoryUpdates)
        assertEquals(1, second.inventoryUpdates)
        assertEquals(stack, inventory.getItem(0))
    }

    @Test
    public fun `force rerender includes a player inventory owner`() {
        val player = TrackingPlayer(server, "owner")
        server.addPlayer(player)

        player.inventory.forceRerender()

        assertEquals(1, player.inventoryUpdates)
    }
}

private class TrackingPlayer(
    server: ServerMock,
    name: String,
) : PlayerMock(server, name) {
    var inventoryUpdates: Int = 0
        private set

    override fun updateInventory() {
        inventoryUpdates++
    }
}
