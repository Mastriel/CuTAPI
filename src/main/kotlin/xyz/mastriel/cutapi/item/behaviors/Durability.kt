package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.inventory.meta.Damageable
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.registry.id

class Durability(val maxDamage: Int, val currentDamage: Int = 0) : ItemBehavior(id(Plugin, "custom_durability")) {

    override fun onCreate(item: CuTItemStack) {
        item.handle.editMeta {
            if (it !is Damageable) return@editMeta
            it.setMaxDamage(maxDamage)
            it.damage = currentDamage
        }
    }
}