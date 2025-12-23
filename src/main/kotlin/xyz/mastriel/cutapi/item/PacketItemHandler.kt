package xyz.mastriel.cutapi.item

import com.github.shynixn.mccoroutine.bukkit.*
import com.mojang.datafixers.util.*
import kotlinx.coroutines.*
import net.minecraft.core.*
import net.minecraft.core.component.*
import net.minecraft.network.*
import net.minecraft.network.protocol.game.*
import net.minecraft.network.syncher.*
import net.minecraft.network.syncher.SynchedEntityData.DataValue
import net.minecraft.world.item.*
import net.minecraft.world.item.trading.*
import org.bukkit.*
import org.bukkit.craftbukkit.inventory.*
import org.bukkit.craftbukkit.util.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.inventory.*
import org.bukkit.inventory.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.nms.PacketListener
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.periodic.*
import xyz.mastriel.cutapi.utils.*
import xyz.mastriel.cutapi.utils.personalized.*
import java.util.*


/**
 * This class is responsible for handling packets related to items.
 * It renders the player's inventory every 5 ticks.
 */
@UsesNMS
internal object PacketItemHandler : Listener, PacketListener {

    @PacketHandler(EventPriority.HIGH)
    fun itemSlotChangePacketEvent(event: PacketEvent<ClientboundContainerSetSlotPacket>): ClientboundContainerSetSlotPacket {
        val item = CraftItemStack.asBukkitCopy(event.packet.item)
        val customItemStack = item.wrap()?.withViewer(event.player) ?: return event.packet

        val newPacket = ClientboundContainerSetSlotPacket(
            event.packet.containerId,
            event.packet.stateId,
            event.packet.slot,
            customItemStack.nms()
        )

        return newPacket
    }

    @PacketHandler(EventPriority.HIGH)
    fun handleMerchantOffers(event: PacketEvent<ClientboundMerchantOffersPacket>): ClientboundMerchantOffersPacket {
        val newOffers = MerchantOffers()

        fun itemCost(mojangItemStack: MojangItemStack?): Optional<ItemCost> {
            if (mojangItemStack == null) return Optional.empty<ItemCost>()
            val componentPredicate = DataComponentExactPredicate.allOf(mojangItemStack.components)
            return Optional.of(ItemCost(mojangItemStack.itemHolder, mojangItemStack.count, componentPredicate))
        }
        for (offer in event.packet.offers) {
            newOffers += MerchantOffer(
                itemCost(offer.costA).get(),
                itemCost(renderIfNeeded(event.player, offer.getCostB().bukkit().toAgnostic()).nms()),
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
            .map { it.wrap()?.withViewer(player) ?: it }
            .reversed() // Bukkit stores these in reverse

        val mainContents = player.inventory.contents
            .map { it?.clone() ?: ItemStack(Material.AIR) }
            .map { it.wrap()?.withViewer(player) ?: it }

        val offhand = listOf(player.inventory.itemInOffHand)
            .map { it.clone() }
            .map { it.wrap()?.withViewer(player) ?: it }

//        var cursor = listOf(player.itemOnCursor)
//            .map { it.clone() }
//            .map { if (it.isCustom) CuTItemStack.wrap(it) withViewer player else it }
//
//        if (cursor.first().type == Material.AIR) cursor = emptyList()

        // crafting is only available in survival mode
        val craftingSlots = if (player.openInventory.type == InventoryType.CRAFTING) {
            player.openInventory.topInventory.contents
                .map { it?.clone() ?: ItemStack(Material.AIR) }
                .map { it.wrap()?.withViewer(player) ?: it }
        } else {
            List(5) { ItemStack(Material.AIR) }
        }

        val hotbar = mainContents.take(9)
        val storedInventory = mainContents.subList(9, 36)

        return listOf(craftingSlots, armorContents, storedInventory, hotbar, offhand)
            .flatten()
            .map { it.nms() }
            .toNonNullList()
    }

    @PacketHandler
    fun handleContainerUpdate(event: PacketEvent<ClientboundContainerSetContentPacket>): ClientboundContainerSetContentPacket {
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
    fun handleEquipmentChange(event: PacketEvent<ClientboundSetEquipmentPacket>): ClientboundSetEquipmentPacket {
        val player = event.player

        val slots = event.packet.slots.map {
            val itemStack = it.second.bukkit().toAgnostic()
            val renderedItem = renderIfNeeded(player, itemStack).nms()
            Pair(it.first, renderedItem)
        }

        return ClientboundSetEquipmentPacket(event.packet.entity, slots)
    }

    private fun getServerSideStack(item: MojangItemStack): MojangItemStack {
        val itemStack = item.bukkit().wrap() ?: return item
        return itemStack.getPrerenderItemStack(item.count)?.nms() ?: item
    }

    @PacketHandler
    fun clickInventory(event: PacketEvent<ServerboundContainerClickPacket>): ServerboundContainerClickPacket {
        @Suppress("DEPRECATION")
        val evilItem =
            HashedStack.ActualItem(Items.DIRT.builtInRegistryHolder(), -1, HashedPatchMap(emptyMap(), emptySet()))

        // evil hack from hell
        // hashedstack is evil blah blah blah
        // just send something that causes a desync and forces the server to try to correct it
        // not efficient however the other option involves some horrible caching
        return ServerboundContainerClickPacket(
            event.packet.containerId,
            event.packet.stateId,
            event.packet.slotNum,
            event.packet.buttonNum,
            event.packet.clickType,
            event.packet.changedSlots,
            evilItem
        )

//        return ServerboundContainerClickPacket(
//            event.packet.containerId,
//            event.packet.stateId,
//            event.packet.slotNum,
//            event.packet.buttonNum,
//            event.packet.clickType,
//            event.packet.changedSlots.mapValuesTo(Int2ObjectArrayMap()) { (_, item) -> getServerSideStack(item) },
//            getServerSideStack(event.packet.carriedItem)
//        )
    }

    @PacketHandler
    fun handleCreativeSetSlot(event: PacketEvent<ServerboundSetCreativeModeSlotPacket>): ServerboundSetCreativeModeSlotPacket {
        val item = event.packet.itemStack
        val pdc = item.bukkit().itemMeta?.persistentDataContainer
        val isRendered = pdc?.get(
            NamespacedKey(Plugin, "IsDisplay"),
            PersistentDataType.BOOLEAN
        ) == true

        val wrapped = event.packet.itemStack.bukkit().wrap()
        if (isRendered && wrapped != null) {
            val amount = event.packet.itemStack.count
            val itemStack = wrapped.getPrerenderItemStack(amount)


            if (itemStack == null) {
                Plugin.warn("ItemStack Prerender is null! This is a bug!".colored)
            } else {
                return ServerboundSetCreativeModeSlotPacket(
                    event.packet.slotNum,
                    itemStack.nms()
                )
            }
        }

        if (event.packet.itemStack.item == CraftMagicNumbers.getItem(Material.AIR)) {
            Plugin.launch {
                delay(1.ticks)

                ClientboundContainerSetSlotPacket(
                    0,
                    0,
                    event.packet.slotNum.toInt(),
                    net.minecraft.world.item.ItemStack.EMPTY
                ).sendTo(event.player)
            }
        }

        return ServerboundSetCreativeModeSlotPacket(
            event.packet.slotNum,
            renderIfNeeded(event.player, item.bukkit().toAgnostic()).nms()
        )
    }

    @Periodic(ticks = 5, asyncThread = false)
    fun updatePlayerItems() {
        for (player in onlinePlayers()) {
            // this is responsible for updating the inventory of the player's open window.
            if (player.openWindowId != null) {

                val topInventory = player.openInventory.topInventory
                val bottomInventory = player.openInventory.bottomInventory

                fun sendInventory(inventory: Inventory) {
                    // anvils can be glitchy. dont have time to fix this. very quick fix
                    // is to just make it not update. hooray!
                    if (inventory.type == InventoryType.ANVIL) return

                    val contents = inventory.contents
                        .map { it?.clone() ?: ItemStack(Material.AIR) }
                        .map { it.wrap()?.withViewer(player) ?: it }
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
                return
            }

            // only send the packet to update the whole inventory if the player is not in creative mode
            // in creative mode, the client handles the cursor slot in its entirety. we can't see what item
            // is in the cursor slot at any given time since the client doesn't send that information to the server.
            // https://github.com/PaperMC/Paper/issues/7797#issuecomment-1120472278
            if (player.gameMode != GameMode.CREATIVE) {

                val packet = ClientboundContainerSetContentPacket(
                    0,
                    0,
                    renderPlayerInventory(player),
                    customCursorItem(player).nms()
                )

                packet.sendTo(player)
            } else {
                // we have to send a separate packet for each slot in the player's inventory
                // because the client doesn't send the cursor slot to the server in creative mode.
                // this stupid as fuck.

                val inventory = renderPlayerInventory(player)

                inventory.map { it.bukkit() }.forEachIndexed { index, item ->

                    if (item.wrap() != null) {
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


        }
    }

    private fun customCursorItem(player: Player): ItemStack {
        // Bukkit.broadcast("Cursor: ${player.openInventory.cursor}".colored)
        val item = player.itemOnCursor.wrap()?.withViewer(player)
        if (item != null) {
            return item
        }
        return player.itemOnCursor
    }

    @PacketHandler(EventPriority.HIGH)
    fun handleEntities(event: PacketEvent<ClientboundSetEntityDataPacket>): ClientboundSetEntityDataPacket {
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

    fun renderIfNeeded(viewer: Player?, stack: AgnosticItemStack): ItemStack {
        return when (stack) {
            is AgnosticItemStack.Custom -> stack.custom() withViewer viewer
            is AgnosticItemStack.Vanilla -> stack.vanilla()
        }
    }

    @PacketHandler(EventPriority.HIGH)
    fun openWindow(event: PacketEvent<ClientboundOpenScreenPacket>): ClientboundOpenScreenPacket {
        windowIds[event.player] = event.packet.containerId
        return event.packet
    }

    @PacketHandler(EventPriority.HIGH)
    fun closeWindowClientbound(event: PacketEvent<ClientboundContainerClosePacket>): ClientboundContainerClosePacket {
        if (event.packet.containerId == windowIds[event.player]) {
            windowIds.remove(event.player)
        }
        return event.packet

    }

    @PacketHandler(EventPriority.HIGH)
    fun closeWindowServerbound(event: PacketEvent<ServerboundContainerClosePacket>): ServerboundContainerClosePacket {
        if (event.packet.containerId == windowIds[event.player]) {
            windowIds.remove(event.player)
        }
        return event.packet
    }

    private val windowIds = mutableMapOf<Player, Int>()

    internal val Player.openWindowId get() = windowIds[this]

    internal fun CuTItemStack.setPrerenderItemStack(prerender: ItemStack) {

        set("PrerenderItemStack", prerender.clone().also { it.amount = 1 }, ItemStackTagConverter)
    }

    internal fun CuTItemStack.getPrerenderItemStack(amount: Int): ItemStack? {
        return get("PrerenderItemStack", ItemStackTagConverter)?.also { it.amount = amount }
    }

    internal fun CuTItemStack.hasPrerenderStack(): Boolean {
        return has("PrerenderItemStack")
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