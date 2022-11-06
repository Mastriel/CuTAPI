package xyz.mastriel.cutapi.items.behaviors

import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.registry.id
import java.util.*

object Unstackable : ItemBehavior(id(Plugin, "unstackable")) {

    override fun onCreate(item: CuTItemStack) {
        val data = getData(item)
        data.setUUID("UnstackableUUID", UUID.randomUUID())
    }
}