package xyz.mastriel.cutapi.gui

import org.jetbrains.annotations.*

public sealed class GuiType {

    public data object Anvil : GuiType()
    public data object CraftingTable : GuiType()
    public data object SmithingTable : GuiType()
    public data object Dropper : GuiType()
    public data object Hopper : GuiType()
    public data class Chest(val size: @Range(from = 1, to = 6) Int) : GuiType()
}