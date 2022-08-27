package xyz.mastriel.cutapi.items.components

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent

class ItemComponentEvents : Listener {

    @EventHandler
    fun onItemObtain(e: CustomItemObtainEvent) {
        e.components.forEach { it.onObtain(e) }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val item = e.item ?: return
        val customItem = CustomItemStack.fromVanillaOrNull(item) ?: return

        customItem.components.forEach { it.onInteract(customItem, e) }
    }
}