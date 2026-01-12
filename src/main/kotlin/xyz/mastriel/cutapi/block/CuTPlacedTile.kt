package xyz.mastriel.cutapi.block

import org.bukkit.*
import org.bukkit.block.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*


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
        customTileEntityTag(id(Plugin, "type"), CustomTileEntity.Unknown)


}

public open class CuTPlacedBlock(handle: Block) : CuTPlacedTile(handle) {
    final override val typeTag: NotNullTag<String, CustomBlock<*>> =
        customBlockTag(id(Plugin, "type"), CustomBlock.Unknown)

}