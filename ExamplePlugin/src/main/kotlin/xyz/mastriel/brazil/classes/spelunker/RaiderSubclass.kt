package xyz.mastriel.brazil.classes.spelunker

import xyz.mastriel.brazil.Plugin
import xyz.mastriel.brazil.classes.PlayerSubclass
import xyz.mastriel.cutapi.registry.id

object RaiderSubclass : PlayerSubclass(SpelunkerClass, id(Plugin, "raider")) {
    override val name: String = "Raider"
}