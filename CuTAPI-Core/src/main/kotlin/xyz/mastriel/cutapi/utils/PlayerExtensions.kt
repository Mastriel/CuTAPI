package xyz.mastriel.cutapi.utils

import org.bukkit.entity.Player
import xyz.mastriel.cutapi.items.CustomItemStack


fun Player.updateCustomItems() {
    val modifiedContents = inventory.contents.map {
        if (it == null) return@map null
        val customItemStack = CustomItemStack.fromVanillaOrNull(it) ?: return@map it

        customItemStack.toBukkitItemStack()
    }
    inventory.setContents(modifiedContents.filterNotNull().toTypedArray())
}