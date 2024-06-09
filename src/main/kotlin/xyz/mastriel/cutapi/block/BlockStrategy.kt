package xyz.mastriel.cutapi.block

import org.bukkit.*

public sealed class BlockStrategy {

    public data object NoteBlock : BlockStrategy()
    public data object Mushroom : BlockStrategy()
    public data class Vanilla(val material: Material) : BlockStrategy()
    public data object FakeEntity : BlockStrategy()
}


