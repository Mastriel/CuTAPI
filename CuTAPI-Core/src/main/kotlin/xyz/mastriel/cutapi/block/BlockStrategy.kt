package xyz.mastriel.cutapi.block

import org.bukkit.Material

sealed class BlockStrategy {

    data object NoteBlock : BlockStrategy()
    data object Mushroom : BlockStrategy()
    data class Vanilla(val material: Material) : BlockStrategy()
    data object FakeEntity : BlockStrategy()
}


