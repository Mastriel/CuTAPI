package xyz.mastriel.cutapi.pdc.tags.converters

import xyz.mastriel.cutapi.registry.*

public object IdentifierTagConverter :
    TagConverter<String, Identifier>(String::class, Identifier::class) {

    override fun fromPrimitive(primitive: String): Identifier {
        return id(primitive)
    }

    override fun toPrimitive(complex: Identifier): String {
        return complex.toString()
    }


}

