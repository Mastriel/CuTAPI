package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.events.CustomItemObtainEvent

class ItemBehaviorEvents : Listener {

    @EventHandler
    fun onObtain(event: CustomItemObtainEvent) {
        event.behaviors.forEach {
            it.onObtain(event.player, event.item, event)
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
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
    fun onOffhandSwap(event: PlayerSwapHandItemsEvent) {
        val item = event.offHandItem ?: return
        if (!item.isCustom) return
        val customItem = item.wrap()
        customItem?.getAllBehaviors()?.forEach {
            it.onOffhandEquip(event.player, customItem, event)
        }
    }

    @EventHandler
    fun onOffhandInventoryPlace(event: InventoryClickEvent) {
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
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
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
}