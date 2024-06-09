package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.registry.*

public fun customBlock(id: Identifier, builder: BlockDescriptorBuilder.() -> Unit): CustomBlock<CuTPlacedBlock> {
    val descriptor = BlockDescriptorBuilder().apply(builder).build()

    return CustomBlock(id, descriptor, CuTPlacedBlock::class)
}


public fun customTileEntity(
    id: Identifier,
    builder: TileEntityDescriptorBuilder.() -> Unit
): CustomTileEntity<CuTPlacedTileEntity> {
    val descriptor = TileEntityDescriptorBuilder().apply(builder).build()

    return CustomTileEntity(id, descriptor, CuTPlacedTileEntity::class)
}
