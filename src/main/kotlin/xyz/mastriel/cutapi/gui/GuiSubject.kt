package xyz.mastriel.cutapi.gui

import xyz.mastriel.cutapi.block.CuTPlacedTileEntity
import xyz.mastriel.cutapi.block.inventory.BlockInventory

public sealed interface GuiSubject {
    public data object Standalone : GuiSubject

    public data class Block(
        public val tile: CuTPlacedTileEntity,
        public val inventory: BlockInventory,
    ) : GuiSubject
}
