package xyz.mastriel.cutapi.block

import org.bukkit.*
import org.bukkit.block.*
import xyz.mastriel.cutapi.pdc.tags.*


public sealed class CuTPlacedTile(
    public val handle: Block,
) : TagContainer by BlockDataTagContainer(handle) {

    public val location: Location by handle::location
    public val chunk: Chunk by handle::chunk

    public fun vanilla(): Block = handle

    protected abstract val typeTag: NotNullTag<String, out CustomTile<*>>

    @Suppress("UNCHECKED_CAST")
    public var type: CustomTile<*>
        get() = typeTag.get()
        set(value) {
            (typeTag as NotNullTag<String, CustomTile<*>>).store(value)
        }


}


public open class CuTPlacedTileEntity(
    handle: Block
) : CuTPlacedTile(handle) {


    final override val typeTag: NotNullTag<String, CustomTileEntity<*>> =
        customTileEntityTag("type", CustomTileEntity.Unknown)


}

public open class CuTPlacedBlock(handle: Block) : CuTPlacedTile(handle) {

    final override val typeTag: NotNullTag<String, CustomBlock<*>> = customBlockTag("type", CustomBlock.Unknown)

}