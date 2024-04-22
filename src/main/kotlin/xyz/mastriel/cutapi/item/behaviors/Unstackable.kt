package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.behavior.hasBehavior
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.pdc.tags.nullableUuidTag
import xyz.mastriel.cutapi.pdc.tags.setUUID
import xyz.mastriel.cutapi.pdc.tags.uuidTag
import xyz.mastriel.cutapi.registry.id
import java.util.*

object Unstackable : ItemBehavior(id(Plugin, "unstackable")), Listener {

    override fun onCreate(item: CuTItemStack) {
        var uuid by getData(item).nullableUuidTag("UnstackableUUID")
        item.handle.amount = 1
        uuid = UUID.randomUUID()
    }

}