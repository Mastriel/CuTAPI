package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier


sealed interface CustomTile<T: CuTPlacedTile> : Identifiable {
    val descriptor: TileDescriptor
}

class CustomBlock<T: CuTPlacedTile>(
    override val id: Identifier,
    override val descriptor: TileDescriptor
) : CustomTile<T> {

}