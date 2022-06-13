package xyz.mastriel.cutapi.items

import kotlinx.serialization.Serializable
import org.bukkit.inventory.ItemStack


@Serializable
class CustomItemStack(val customMaterial: CustomMaterial, val quantity: Int) {

    init {
        customMaterial.onCreate(this)
    }


    fun toBukkitItemStack() : ItemStack {

    }
}