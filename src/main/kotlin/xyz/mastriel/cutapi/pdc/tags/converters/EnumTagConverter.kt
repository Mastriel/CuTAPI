package xyz.mastriel.cutapi.pdc.tags.converters

import kotlin.reflect.*

public class EnumTagConverter<T : Enum<T>>(kClass: KClass<T>) :
    TagConverter<String, T>(String::class, kClass) {


    override fun fromPrimitive(primitive: String): T {
        return complexClass.java.enumConstants
            .first { it.name == primitive }
    }

    override fun toPrimitive(complex: T): String {
        return complex.name
    }

}
