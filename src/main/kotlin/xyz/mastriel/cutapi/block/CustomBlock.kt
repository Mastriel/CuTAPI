package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier


sealed interface CustomTile<T> : Identifiable {
    val descriptor: TileDescriptor
}

class CustomBlock<T>(
    override val id: Identifier,
    override val descriptor: BlockDescriptor
) : CustomTile<T> {

}

class CustomTileEntity<T>(
    override val id: Identifier,
    override val descriptor: TileEntityDescriptor
) : CustomTile<T> {

}