package xyz.mastriel.cutapi.item.behaviors

import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.pdc.tags.nullableUuidTag
import xyz.mastriel.cutapi.pdc.tags.setUUID
import xyz.mastriel.cutapi.pdc.tags.uuidTag
import xyz.mastriel.cutapi.registry.id
import java.util.*

object Unstackable : ItemBehavior(id(Plugin, "unstackable")) {

    override fun onCreate(item: CuTItemStack) {
        var uuid by getData(item).nullableUuidTag("UnstackableUUID")
        uuid = UUID.randomUUID()
    }
}