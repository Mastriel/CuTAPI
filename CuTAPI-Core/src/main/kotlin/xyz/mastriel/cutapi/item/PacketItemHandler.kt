package xyz.mastriel.cutapi.item

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.packets.PacketEvent
import xyz.mastriel.cutapi.packets.PacketHandler
import xyz.mastriel.cutapi.packets.PacketListener
import xyz.mastriel.cutapi.packets.wrappers.*
import xyz.mastriel.cutapi.packets.wrappers.ClientboundCloseContainerPacket
import xyz.mastriel.cutapi.packets.wrappers.ClientboundOpenScreenPacket
import xyz.mastriel.cutapi.packets.wrappers.ClientboundSetContainerContentPacket
import xyz.mastriel.cutapi.packets.wrappers.ClientboundSetSlotPacket
import xyz.mastriel.cutapi.periodic.Periodic
import xyz.mastriel.cutapi.utils.personalized.withViewer


// Handles server/client differences in items.
internal object PacketItemHandler : PacketListener, Listener {

    @PacketHandler(EventPriority.MONITOR)
    fun itemSlotChangePacketEvent(event: PacketEvent<ClientboundSetSlotPacket>) {
        if (!event.packet.itemStack.isCustom) return
        val itemStack = CuTItemStack.wrap(event.packet.itemStack.clone()) withViewer event.player

        event.packet.itemStack = itemStack

    }


    fun renderPlayerInventory(player: Player) : List<ItemStack> {
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

        val cursor = listOf(player.itemOnCursor)
            .map { it.clone() ?: ItemStack(Material.AIR) }
            .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }

        println(cursor.firstOrNull()?.type)

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

            val packet = ClientboundSetContainerContentPacket(0, renderPlayerInventory(player), customCursorItem(player))

            packet.sendPacket(player)

            if (player.openWindowId != null) {
                val topInventory = player.openInventory.topInventory
                val bottomInventory = player.openInventory.bottomInventory

                fun sendInventory(inventory: Inventory) {
                    val contents = inventory.contents
                        .map { it?.clone() ?: ItemStack(Material.AIR) }
                        .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }

                    val inventoryPacket = ClientboundSetContainerContentPacket(player.openWindowId!!, contents, customCursorItem(player))
                    inventoryPacket.sendPacket(player)
                }

                if (topInventory !is PlayerInventory && topInventory.type != InventoryType.CRAFTING) {
                    sendInventory(topInventory)
                }

                if (bottomInventory !is PlayerInventory && topInventory.type != InventoryType.CRAFTING) {
                    sendInventory(bottomInventory)
                }
            }


        }
    }

    private fun customCursorItem(player: Player) : ItemStack {
        if (player.openInventory.cursor.isCustom) {
            return player.openInventory.cursor.wrap()!! withViewer player
        }
        return player.openInventory.cursor
    }

    @PacketHandler(EventPriority.HIGH)
    fun openWindow(event: PacketEvent<ClientboundOpenScreenPacket>) {
        windowIds[event.player] = event.packet.windowId
    }

    @PacketHandler(EventPriority.HIGH)
    fun closeWindow1(event: PacketEvent<ClientboundCloseContainerPacket>) {
        if (event.packet.windowId == windowIds[event.player]) {
            windowIds.remove(event.player)
        }
    }

    @PacketHandler(EventPriority.HIGH)
    fun closeWindow2(event: PacketEvent<ServerboundCloseContainerPacket>) {
        if (event.packet.windowId == windowIds[event.player]) {
            windowIds.remove(event.player)
        }
    }

    private val windowIds = mutableMapOf<Player, Int>()

    internal val Player.openWindowId get() = windowIds[player]

    /*
    @EventHandler
    fun onItemClick(e: InventoryClickEvent) {
        e.viewers.forEach {
            it.sendMessage("Bukkit: ${e.slot} | Notchian: ${e.rawSlot} | Type: ${e.clickedInventory?.type}")
        }
    }
    */
}