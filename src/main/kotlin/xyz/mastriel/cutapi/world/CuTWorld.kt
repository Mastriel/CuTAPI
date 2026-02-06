package xyz.mastriel.cutapi.world

import org.bukkit.*
import xyz.mastriel.cutapi.*

public class CuTWorld(public val handle: World) {


    public fun cleanup() {

    }

    public fun initialize() {

    }
}

public fun World.wrap() {
    CuTAPI
}