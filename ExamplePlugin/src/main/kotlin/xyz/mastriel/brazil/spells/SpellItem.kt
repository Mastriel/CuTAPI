package xyz.mastriel.brazil.spells

import org.bukkit.Material
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.ItemDescriptor
import xyz.mastriel.cutapi.registry.Identifier

abstract class SpellItem(id: Identifier, material: Material) : CustomItem(id, material) {
}