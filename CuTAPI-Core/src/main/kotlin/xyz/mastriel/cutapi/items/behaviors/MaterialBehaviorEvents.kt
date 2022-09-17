package xyz.mastriel.cutapi.items.behaviors

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CuTItemStack.Companion.isCustom
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent

class MaterialBehaviorEvents : Listener {

    @EventHandler
    fun onObtain(event: CustomItemObtainEvent) {
        event.components.forEach {
            it.onObtain(event.player, event.item, event)
        }
    }

    @EventHandler
    fun onOffhandSwap(event: PlayerSwapHandItemsEvent) {
        val item = event.offHandItem ?: return
        if (!item.isCustom) return
        val customItem = CuTItemStack(item)
        customItem.getAllBehaviors().forEach {
            it.onOffhandEquip(event.player, customItem, event)
        }
    }

    @EventHandler
    fun onOffhandInventoryPlace(event: InventoryClickEvent) {
        if (event.slot != 40) return
        val player = event.whoClicked as? Player ?: return
        val item = event.cursor ?: return
        if (!item.isCustom) return
        val customItem = CuTItemStack(item)

        customItem.getAllBehaviors().forEach {
            it.onOffhandEquip(player, customItem, event)
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return

        val damager = event.damager as? LivingEntity ?: return
        val victim = event.entity as? LivingEntity ?: return

        val heldItem = if (damager is Player) damager.inventory.itemInMainHand else damager.activeItem
        if (!heldItem.isCustom) return

        val customItem = CuTItemStack(heldItem)

        customItem.getAllBehaviors().forEach {
            it.onDamageEntity(damager, victim, customItem, event)
        }
    }
}