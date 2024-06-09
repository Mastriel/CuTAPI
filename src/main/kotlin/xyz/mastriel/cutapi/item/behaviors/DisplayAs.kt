package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

/**
 * Makes a custom item display as this material. This is purely client sided (except when
 * the holder is in creative mode, as creative mode enables client-sided changes like this
 * to occur without the server arguing)
 */
public class DisplayAs(public val material: Material) : ItemBehavior(id(Plugin, "display_as")) {

    override fun onRender(viewer: Player?, item: CuTItemStack) {
        item.handle.type = material
    }

}
