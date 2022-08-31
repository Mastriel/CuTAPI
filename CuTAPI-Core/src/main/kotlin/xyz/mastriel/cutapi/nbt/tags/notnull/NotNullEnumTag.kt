package xyz.mastriel.cutapi.nbt.tags.notnull

import de.tr7zw.changeme.nbtapi.NBTCompound
import kotlin.reflect.KClass

class NotNullEnumTag<T : Enum<T>>(key: String, compound: NBTCompound, kClass: KClass<T>, default: T) :
    NotNullTag<T>(key, compound, kClass, default) {

    override fun get(): T {
        val enumValue = compound.getString(key)
        val value = kClass.java.enumConstants
            .map { println(it.name); it }
            .first { it.name == enumValue } ?: default
        println(value.name + " & " + enumValue)
        return value
    }

    override fun store(value: T) {
        compound.setString(key, value.name)
    }
}
