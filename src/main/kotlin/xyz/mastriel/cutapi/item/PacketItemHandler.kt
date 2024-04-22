package xyz.mastriel.cutapi.item

import com.mojang.datafixers.util.Pair
import net.minecraft.core.NonNullList
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData.DataValue
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.nms.PacketEvent
import xyz.mastriel.cutapi.nms.nms
import xyz.mastriel.cutapi.periodic.Periodic


/**
 * This class is responsible for handling packets related to items.
 * It renders the player's inventory every 5 ticks.
 */
@UsesNMS
internal object PacketItemHandler : Listener, PacketListener {

    @PacketHandler(EventPriority.HIGH)
    fun itemSlotChangePacketEvent(event: PacketEvent<ClientboundContainerSetSlotPacket>): ClientboundContainerSetSlotPacket {
        val item = CraftItemStack.asBukkitCopy(event.packet.item)
        if (!item.isCustom) return event.packet

        val customItemStack = CuTItemStack.wrap(item) withViewer event.player

        val newPacket = ClientboundContainerSetSlotPacket(
            event.packet.containerId,
            event.packet.stateId,
            event.packet.slot,
            customItemStack.nms()
        )

        return newPacket
    }

    @PacketHandler(EventPriority.HIGH)
    fun handleMerchantOffers(event: PacketEvent<ClientboundMerchantOffersPacket>) : ClientboundMerchantOffersPacket {
        val newOffers = MerchantOffers()

        for (offer in event.packet.offers) {
            newOffers += MerchantOffer(
                renderIfNeeded(event.player, offer.baseCostA.bukkit().toAgnostic()).nms(),
                renderIfNeeded(event.player, offer.costB.bukkit().toAgnostic()).nms(),
                renderIfNeeded(event.player, offer.result.bukkit().toAgnostic()).nms(),
                offer.uses,
                offer.maxUses,
                offer.xp,
                offer.priceMultiplier,
                offer.demand
            )
        }

        return ClientboundMerchantOffersPacket(
            event.packet.containerId,
            newOffers,
            event.packet.villagerLevel,
            event.packet.villagerXp,
            event.packet.showProgress(),
            event.packet.canRestock()
        )
    }


    // This does not include the cursor! In 1.20.4, this seems to now be its own field.
    // Or maybe it was always like that. Either way, if you try to send the cursor it will
    // freeze the client and force them to restart their game. Which is bad.
    fun renderPlayerInventory(player: Player): NonNullList<MojangItemStack> {
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

//        var cursor = listOf(player.itemOnCursor)
//            .map { it.clone() }
//            .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }
//
//        if (cursor.first().type == Material.AIR) cursor = emptyList()

        // crafting is only available in survival mode
        val craftingSlots = if (player.openInventory.type == InventoryType.CRAFTING) {
            player.openInventory.topInventory.contents
                .map { it?.clone() ?: ItemStack(Material.AIR) }
                .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }
        } else {
            List(5) { ItemStack(Material.AIR) }
        }

        val hotbar = mainContents.take(9)
        val storedInventory = mainContents.subList(9, 36)

        return listOf(craftingSlots, armorContents, storedInventory, hotbar, offhand /* cursor */)
            .flatten()
            .map { it.nms() }
            .toNonNullList()
    }

    @PacketHandler
    fun handleContainerUpdate(event: PacketEvent<ClientboundContainerSetContentPacket>) : ClientboundContainerSetContentPacket {
        val player = event.player
        val items = event.packet.items

        val newItems = items.map {
            val itemStack = it.bukkit().toAgnostic()
            renderIfNeeded(player, itemStack).nms()
        }

        return ClientboundContainerSetContentPacket(
            event.packet.containerId,
            event.packet.stateId,
            newItems.toNonNullList(),
            renderIfNeeded(player, event.packet.carriedItem.bukkit().toAgnostic()).nms()
        )
    }

    @PacketHandler
    fun handleEquipmentChange(event: PacketEvent<ClientboundSetEquipmentPacket>) : ClientboundSetEquipmentPacket {
        val player = event.player

        val slots = event.packet.slots.map {
            val itemStack = it.second.bukkit().toAgnostic()
            val renderedItem = renderIfNeeded(player, itemStack).nms()
            Pair(it.first, renderedItem)
        }

        return ClientboundSetEquipmentPacket(event.packet.entity, slots)
    }

    @PacketHandler
    fun handleCreativeSetSlot(event: PacketEvent<ServerboundSetCreativeModeSlotPacket>) : ServerboundSetCreativeModeSlotPacket {
        val item = event.packet.item

        return ServerboundSetCreativeModeSlotPacket(
            event.packet.slotNum,
            renderIfNeeded(event.player, item.bukkit().toAgnostic()).nms()
        )
    }

    @Periodic(ticks = 5, asyncThread = false)
    fun updatePlayerItems() {
        for (player in Bukkit.getOnlinePlayers().filterNotNull()) {

            // only send the packet to update the whole inventory if the player is not in creative mode
            // in creative mode, the client handles the cursor slot in its entirety. we can't see what item
            // is in the cursor slot at any given time since the client doesn't send that information to the server.
            // https://github.com/PaperMC/Paper/issues/7797#issuecomment-1120472278
            if (player.gameMode != GameMode.CREATIVE) {

                val packet = ClientboundContainerSetContentPacket(
                    0,
                    // having no revision/stateid works, however it sends unnecessary packets
                    // prs are welcome to fix this
                    // https://wiki.vg/Protocol#Click_Container
                    0,
                    renderPlayerInventory(player),
                    customCursorItem(player).nms()
                )

                packet.sendTo(player)
            } else {
                // so here's what's up. we have to send a separate packet for each slot in the player's inventory
                // because the client doesn't send the cursor slot to the server in creative mode.
                // this stupid as fuck.

                val inventory = renderPlayerInventory(player)

                inventory.map { it.bukkit() }.forEachIndexed { index, item ->

                    if (item.isCustom) {
                        val packet = ClientboundContainerSetSlotPacket(
                            0,
                            0,
                            index,
                            item.nms()
                        )
                        packet.sendTo(player)
                    }
                }
            }

            // this is responsible for updating the inventory of the player's open window.
            if (player.openWindowId != null) {

                val topInventory = player.openInventory.topInventory
                val bottomInventory = player.openInventory.bottomInventory

                fun sendInventory(inventory: Inventory) {
                    val contents = inventory.contents
                        .map { it?.clone() ?: ItemStack(Material.AIR) }
                        .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }
                        .map { it.nms() }
                        .toNonNullList()


                    val inventoryPacket = ClientboundContainerSetContentPacket(
                        player.openWindowId!!,
                        0,
                        contents,
                        customCursorItem(player).nms()
                    )
                    inventoryPacket.sendTo(player)
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

    private fun customCursorItem(player: Player): ItemStack {
        // Bukkit.broadcast("Cursor: ${player.openInventory.cursor}".colored)
        if (player.itemOnCursor.isCustom) {
            return player.itemOnCursor.wrap()!! withViewer player
        }
        return player.itemOnCursor
    }

    @PacketHandler(EventPriority.HIGH)
    fun handleEntities(event: PacketEvent<ClientboundSetEntityDataPacket>) : ClientboundSetEntityDataPacket {
        val packet = event.packet
        val player = event.player


        val data = packet.packedItems ?: return packet
        val newItems = data.map {
            val value = it.value
            if (value is MojangItemStack) {
                val itemStack = value.bukkit().toAgnostic()
                DataValue(it.id, EntityDataSerializers.ITEM_STACK, renderIfNeeded(player, itemStack).nms())
            } else {
                it
            }
        }

        return ClientboundSetEntityDataPacket(packet.id, newItems)
    }

    fun renderIfNeeded(viewer: Player, stack: AgnosticItemStack) : ItemStack {
        return when (stack) {
            is AgnosticItemStack.Custom -> stack.custom() withViewer viewer
            is AgnosticItemStack.Vanilla -> stack.vanilla()
        }
    }

    @PacketHandler(EventPriority.HIGH)
    fun openWindow(event: PacketEvent<ClientboundOpenScreenPacket>) : ClientboundOpenScreenPacket {
        windowIds[event.player] = event.packet.containerId
        return event.packet
    }

    @PacketHandler(EventPriority.HIGH)
    fun closeWindowClientbound(event: PacketEvent<ClientboundContainerClosePacket>) : ClientboundContainerClosePacket {
        if (event.packet.containerId == windowIds[event.player]) {
            windowIds.remove(event.player)
        }
        return event.packet

    }

    @PacketHandler(EventPriority.HIGH)
    fun closeWindowServerbound(event: PacketEvent<ServerboundContainerClosePacket>) : ServerboundContainerClosePacket {
        if (event.packet.containerId == windowIds[event.player]) {
            windowIds.remove(event.player)
        }
        return event.packet
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