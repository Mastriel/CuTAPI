package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.entity.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

public object HideTooltip : ItemBehavior(id(Plugin, "blank_name")) {

    override fun onRender(viewer: Player?, item: CuTItemStack) {
        item.vanilla().editMeta {
            it.isHideTooltip = true
        }
    }
}