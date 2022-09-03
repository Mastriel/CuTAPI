package xyz.mastriel.cutapi.nbt

import de.tr7zw.changeme.nbtapi.NBTItem
import org.bukkit.inventory.ItemStack

class MetapreservingNBTItem(val srcItemStack: ItemStack) : NBTItem(srcItemStack, false) {

    override fun saveCompound() {
        mergeCustomNBT(srcItemStack)
    }
}