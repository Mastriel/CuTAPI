package xyz.mastriel.cutapi.items.behaviors

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.id

object HideAttributes : ItemBehavior(id(Plugin, "hide_attributes")) {

    override fun onRender(viewer: Player?, item: ItemStack) {
        item.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
    }
}