package xyz.mastriel.brazil.classes.spelunker

import xyz.mastriel.brazil.Plugin
import xyz.mastriel.brazil.classes.PlayerClass
import xyz.mastriel.cutapi.registry.id

object SpelunkerClass : PlayerClass(id(Plugin, "spelunker")) {
    override val name: String = "Spelunker"

}