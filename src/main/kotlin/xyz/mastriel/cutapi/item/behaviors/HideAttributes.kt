package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.entity.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public object HideAttributes : ItemBehavior(id(Plugin, "hide_attributes")) {

    override fun onRender(viewer: Player?, item: CuTItemStack) {
        item.handle.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
    }
}

