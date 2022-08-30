package xyz.mastriel.cutapi.items.components

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CuTItemStack.Companion.isCustom
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent

class ItemComponentEvents : Listener {

    @EventHandler
    fun onItemObtain(e: CustomItemObtainEvent) {
        e.components.forEach { it.onObtain(e) }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val item = e.item ?: return
        if (!item.isCustom) return
        val customItem = CuTItemStack(item)

        customItem.getAllComponents().forEach { it.onInteract(customItem, e) }
    }
}