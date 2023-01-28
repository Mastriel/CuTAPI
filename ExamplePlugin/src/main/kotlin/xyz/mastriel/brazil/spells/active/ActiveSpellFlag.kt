package xyz.mastriel.brazil.spells.active


enum class ActiveSpellFlag(val displayLore: String?) {
    CAST_WHILE_MOVING("Castable while moving"),
    UNINTERRUPTABLE("Uninterruptable"),
    USEABLE_IN_STEALTH("Usable while stealthed")
}