package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.*
import org.bukkit.enchantments.*
import org.bukkit.entity.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public object Shiny : ItemBehavior(id(Plugin, "shiny")) {

    override fun onRender(viewer: Player?, item: CuTItemStack) {
        if (item.enchantments.isEmpty()) {
            if (item.material == Material.FISHING_ROD)
                item.handle.addUnsafeEnchantment(Enchantment.INFINITY, 1)

            item.handle.addUnsafeEnchantment(Enchantment.LURE, 1)

            item.handle.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
    }
}