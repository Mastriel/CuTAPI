package xyz.mastriel.cutapi.block

import org.bukkit.Material
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier

class CustomBlock(
    override val id: Identifier,
    val type: Material,
    val item: CustomItem<*>
) : Identifiable {

}