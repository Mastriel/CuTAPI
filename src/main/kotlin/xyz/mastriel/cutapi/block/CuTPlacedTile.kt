package xyz.mastriel.cutapi.block

import org.bukkit.block.Block
import xyz.mastriel.cutapi.pdc.tags.*


sealed class CuTPlacedTile(
    val handle: Block,
) : TagContainer by BlockDataTagContainer(handle) {

    val location by handle::location
    val chunk by handle::chunk

    fun vanilla() = handle

    protected abstract val typeTag: NotNullTag<String, out CustomTile<*>>

    @Suppress("UNCHECKED_CAST")
    var type: CustomTile<*>
        get() = typeTag.get()
        set(value) {
            (typeTag as NotNullTag<String, CustomTile<*>>).store(value)
        }


}


open class CuTPlacedTileEntity(
    handle: Block
) : CuTPlacedTile(handle) {


    final override val typeTag = customTileEntityTag("type", CustomTileEntity.Unknown)


}

open class CuTPlacedBlock(handle: Block) : CuTPlacedTile(handle) {

    final override val typeTag = customBlockTag("type", CustomBlock.Unknown)

}