package xyz.mastriel.cutapi.block

import org.bukkit.Material
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier


interface CustomTile<T: CuTPlacedBlock> : Identifiable

class CustomBlock<T: CuTPlacedBlock>(
    override val id: Identifier
) : CustomTile<T> {

}