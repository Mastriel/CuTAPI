package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.events.*
import xyz.mastriel.cutapi.periodic.*
import xyz.mastriel.cutapi.utils.*

public class ItemBehaviorEvents : Listener {

    @EventHandler
    public fun onObtain(event: CustomItemObtainEvent) {
        event.behaviors.forEach {
            it.onObtain(event.player, event.item, event)
        }
    }

    @EventHandler
    public fun onInteract(event: PlayerInteractEvent) {
        val item = event.player.inventory.itemInMainHand
        if (!item.isCustom) return
        val customItem = item.wrap()

        customItem?.getAllBehaviors()?.forEach {
            when {
                event.action.isLeftClick -> it.onLeftClick(event.player, customItem, event)
                event.action.isRightClick -> it.onRightClick(event.player, customItem, event)
            }
        }
    }

    @EventHandler
    public fun onInteract(event: BlockBreakEvent) {
        val item = event.player.inventory.itemInMainHand
        if (!item.isCustom) return
        val customItem = item.wrap()

        customItem?.getAllBehaviors()?.forEach {
            it.onBreak(event.player, customItem, event)
        }
    }

    @EventHandler
    public fun onDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        if (!item.isCustom) return
        val customItem = item.wrap()

        customItem?.getAllBehaviors()?.forEach {
            it.onDrop(event.player, customItem, event)
        }
    }

    @EventHandler
    public fun onOffhandSwap(event: PlayerSwapHandItemsEvent) {
        val item = event.offHandItem ?: return
        if (!item.isCustom) return
        val customItem = item.wrap()
        customItem?.getAllBehaviors()?.forEach {
            it.onOffhandEquip(event.player, customItem, event)
        }
    }

    @EventHandler
    public fun onOffhandInventoryPlace(event: InventoryClickEvent) {
        if (event.slot != 40) return
        val player = event.whoClicked as? Player ?: return
        val item = player.itemOnCursor
        if (!item.isCustom) return
        val customItem = item.wrap()

        customItem?.getAllBehaviors()?.forEach {
            it.onOffhandEquip(player, customItem, event)
            if (event.isCancelled) player.setItemOnCursor(item)
        }
    }

    @EventHandler
    public fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return

        val damager = event.damager as? LivingEntity ?: return
        val victim = event.entity as? LivingEntity ?: return

        val heldItem = if (damager is Player) damager.inventory.itemInMainHand else damager.activeItem
        if (!heldItem.isCustom) return

        val customItem = heldItem.wrap()!!

        customItem.getAllBehaviors().forEach {
            it.onDamageEntity(damager, victim, customItem, event)
        }
    }

    /**
     * Deals with onTick... functions for [ItemBehavior]. Ran once per tick.
     */
    @Periodic(1)
    public fun tickEvents() {
        for (player in onlinePlayers()) {

            for ((i, item) in player.inventory.withIndex()) {
                val customItem = item?.wrap() ?: continue
                customItem.getAllBehaviors().forEach { b -> b.onTickInInventory(player, customItem, i) }
            }

            val mainHand = player.inventory.itemInMainHand.wrap()
            mainHand?.getAllBehaviors()?.forEach { b -> b.onTickInEitherHand(player, mainHand, HandSlot.MAIN_HAND) }

            val offHand = player.inventory.itemInOffHand.wrap()
            offHand?.getAllBehaviors()?.forEach { b -> b.onTickInEitherHand(player, offHand, HandSlot.OFF_HAND) }

            val armorList = player.inventory.run { listOf(helmet, chestplate, leggings, boots) }
            val slotList = ArmorSlot.entries

            for ((i, piece) in armorList.withIndex()) {
                val wrappedPiece = piece?.wrap()
                wrappedPiece?.getAllBehaviors()
                    ?.forEach { b -> b.onTickEquipped(player, wrappedPiece, slotList[i]) }
            }
        }
    }
}