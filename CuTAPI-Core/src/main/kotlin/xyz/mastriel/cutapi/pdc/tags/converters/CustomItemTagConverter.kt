package xyz.mastriel.cutapi.pdc.tags.converters

import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.registry.id

object CustomItemTagConverter :
    TagConverter<String, CustomItem<*>>(String::class, CustomItem::class) {

    override fun fromPrimitive(primitive: String): CustomItem<*> {
        return CustomItem.get(id(primitive))
    }

    override fun toPrimitive(complex: CustomItem<*>): String {
        return complex.id.toString()
    }

}

