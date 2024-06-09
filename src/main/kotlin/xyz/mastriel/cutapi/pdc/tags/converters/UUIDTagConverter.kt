package xyz.mastriel.cutapi.pdc.tags.converters

import java.util.*

public object UUIDTagConverter : TagConverter<String, UUID>(String::class, UUID::class) {

    override fun fromPrimitive(primitive: String): UUID {
        return UUID.fromString(primitive)
    }

    override fun toPrimitive(complex: UUID): String {
        return complex.toString()
    }


}