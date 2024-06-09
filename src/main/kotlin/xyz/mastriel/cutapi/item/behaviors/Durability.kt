package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.inventory.meta.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public class Durability(public val maxDamage: Int, public val currentDamage: Int = 0) : ItemBehavior(id(Plugin, "custom_durability")) {

    override fun onCreate(item: CuTItemStack) {
        item.handle.editMeta {
            if (it !is Damageable) return@editMeta
            it.setMaxDamage(maxDamage)
            it.damage = currentDamage
        }
    }
}