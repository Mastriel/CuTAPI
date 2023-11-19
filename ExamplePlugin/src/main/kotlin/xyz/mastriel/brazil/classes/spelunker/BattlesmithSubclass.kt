package xyz.mastriel.brazil.classes.spelunker

import xyz.mastriel.brazil.Plugin
import xyz.mastriel.brazil.classes.PlayerSubclass
import xyz.mastriel.cutapi.registry.id

object BattlesmithSubclass : PlayerSubclass(SpelunkerClass, id(Plugin, "battlesmith")) {
    override val name: String = "Battlesmith"
}