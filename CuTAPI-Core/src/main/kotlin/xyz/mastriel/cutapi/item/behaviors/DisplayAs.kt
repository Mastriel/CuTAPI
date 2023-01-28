package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.id

/**
 * Makes a custom item display as this material. This is purely client sided (except when
 * the holder is in creative mode, as creative mode enables client-sided changes like this
 * to occur without the server arguing)
 */
class DisplayAs(val material: Material) : ItemBehavior(id(Plugin, "display_as")) {

    override fun onRender(viewer: Player?, item: ItemStack) {
        item.type = material
    }
}