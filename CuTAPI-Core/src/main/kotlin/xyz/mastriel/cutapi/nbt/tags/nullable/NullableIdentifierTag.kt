package xyz.mastriel.cutapi.nbt.tags.nullable

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id

class NullableIdentifierTag(key: String, compound: NBTCompound, default: Identifier?) :
    NullableTag<Identifier>(key, compound, Identifier::class, default) {

    override fun get(): Identifier? {
        if (isNull()) return null
        val string = compound.getString(key) ?: return null
        return id(string)
    }

    override fun store(value: Identifier?) {
        if (value == null) return storeNull()
        compound.setString(key, value.toString())
    }
}