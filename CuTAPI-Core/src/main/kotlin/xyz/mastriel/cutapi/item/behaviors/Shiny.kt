package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.pdc.tags.nullableUuidTag
import xyz.mastriel.cutapi.registry.id
import java.util.*

object Shiny : ItemBehavior(id(Plugin, "shiny")) {

    override fun onRender(viewer: Player?, item: CuTItemStack) {
        if (item.enchantments.isEmpty()) {
            if (item.material == Material.FISHING_ROD)
                item.handle.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1)

            item.handle.addUnsafeEnchantment(Enchantment.LURE, 1)

            item.handle.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
    }
}