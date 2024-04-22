package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.registry.id

object HideAttributes : ItemBehavior(id(Plugin, "hide_attributes")) {

    override fun onRender(viewer: Player?, item: CuTItemStack) {
        item.handle.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
    }
}

