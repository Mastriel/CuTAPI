package xyz.mastriel.cutapi.item

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.packets.PacketEvent
import xyz.mastriel.cutapi.packets.PacketHandler
import xyz.mastriel.cutapi.packets.PacketListener
import xyz.mastriel.cutapi.packets.wrappers.ClientboundSetContainerContentPacket
import xyz.mastriel.cutapi.packets.wrappers.ClientboundSetSlotPacket
import xyz.mastriel.cutapi.periodic.Periodic


// Handles server/client differences in items.
internal object PacketItemHandler : PacketListener, Listener {

    @PacketHandler(EventPriority.MONITOR)
    fun itemSlotChangePacketEvent(event: PacketEvent<ClientboundSetSlotPacket>) {
        if (!event.packet.itemStack.isCustom) return
        val itemStack = CuTItemStack.wrap(event.packet.itemStack.clone()) withViewer event.player

        event.packet.itemStack = itemStack

    }


    fun renderInventory(player: Player) : List<ItemStack> {
        val armorContents = player.inventory.armorContents
            .map { it?.clone() ?: ItemStack(Material.AIR) }
            .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }
            .reversed() // Bukkit stores these in reverse

        val mainContents = player.inventory.contents
            .map { it?.clone() ?: ItemStack(Material.AIR) }
            .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }

        val offhand = listOf(player.inventory.itemInOffHand)
            .map { it.clone() }
            .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }

        val cursor = listOf(player.openInventory.cursor)
            .map { it?.clone() ?: ItemStack(Material.AIR) }
            .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }

        val craftingSlots = if (player.openInventory.type == InventoryType.CRAFTING) {
            player.openInventory.topInventory.contents
                .map { it?.clone() ?: ItemStack(Material.AIR) }
                .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }
        } else {
            List(5) { ItemStack(Material.AIR) }
        }

        val hotbar = mainContents.take(9)
        val storedInventory = mainContents.subList(9, 36)

        return listOf(craftingSlots, armorContents, storedInventory, hotbar, offhand, cursor).flatten()

    }

    @Periodic(ticks = 5, asyncThread = false)
    fun updatePlayerItems()  {
        for (player in Bukkit.getOnlinePlayers().filterNotNull()) {

            val packet = ClientboundSetContainerContentPacket(items = renderInventory(player))

            packet.sendPacket(player)
        }
    }

    /*
    @EventHandler
    fun onItemClick(e: InventoryClickEvent) {
        e.viewers.forEach {
            it.sendMessage("Bukkit: ${e.slot} | Notchian: ${e.rawSlot} | Type: ${e.clickedInventory?.type}")
        }
    }
    */
}