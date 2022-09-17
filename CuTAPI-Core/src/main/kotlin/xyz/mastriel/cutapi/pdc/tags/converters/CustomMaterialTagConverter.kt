package xyz.mastriel.cutapi.pdc.tags.converters

import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.registry.id

object CustomMaterialTagConverter :
    TagConverter<String, CustomMaterial>(String::class, CustomMaterial::class) {

    override fun fromPrimitive(primitive: String): CustomMaterial {
        return CustomMaterial.get(id(primitive))
    }

    override fun toPrimitive(complex: CustomMaterial): String {
        return complex.id.toString()
    }

}

