package xyz.mastriel.cutapi.gui

import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.inventory.PrepareGrindstoneEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.view.AnvilView

public fun <C> GuiBuilder<C, AnvilView>.onPrepare(
    handler: GuiInventoryEventContext<C, AnvilView, PrepareAnvilEvent>.() -> Unit,
) {
    onEvent(PrepareAnvilEvent::class, handler)
}

public fun <C> GuiBuilder<C, InventoryView>.onPrepareSmithing(
    handler: GuiInventoryEventContext<C, InventoryView, PrepareSmithingEvent>.() -> Unit,
) {
    onEvent(PrepareSmithingEvent::class, handler)
}

public fun <C> GuiBuilder<C, InventoryView>.onPrepareGrindstone(
    handler: GuiInventoryEventContext<C, InventoryView, PrepareGrindstoneEvent>.() -> Unit,
) {
    onEvent(PrepareGrindstoneEvent::class, handler)
}

public fun <C> GuiBuilder<C, InventoryView>.onPrepareCrafting(
    handler: GuiInventoryEventContext<C, InventoryView, PrepareItemCraftEvent>.() -> Unit,
) {
    onEvent(PrepareItemCraftEvent::class, handler)
}
