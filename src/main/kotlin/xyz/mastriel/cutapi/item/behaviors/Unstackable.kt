package xyz.mastriel.cutapi.item.behaviors

import org.bukkit.event.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import java.util.*

public object Unstackable : ItemBehavior(id(Plugin, "unstackable")), Listener {

    override fun onCreate(item: CuTItemStack) {
        var uuid by getData(item).nullableUuidTag("UnstackableUUID")
        item.handle.amount = 1
        uuid = UUID.randomUUID()
    }

}