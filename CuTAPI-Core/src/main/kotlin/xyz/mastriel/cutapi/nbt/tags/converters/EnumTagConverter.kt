package xyz.mastriel.cutapi.nbt.tags.converters

import kotlin.reflect.KClass

class EnumTagConverter<T : Enum<T>>(kClass: KClass<T>) :
    TagConverter<String, T>(String::class, kClass) {


    override fun fromPrimitive(primitive: String): T {
        return complexClass.java.enumConstants
            .map { println(it.name); it }
            .first { it.name == primitive }
    }

    override fun toPrimitive(complex: T): String {
        return complex.name
    }

}
