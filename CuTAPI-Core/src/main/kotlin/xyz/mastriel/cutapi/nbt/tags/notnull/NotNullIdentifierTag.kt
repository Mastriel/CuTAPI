package xyz.mastriel.cutapi.nbt.tags.notnull

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id

class NotNullIdentifierTag(key: String, compound: NBTCompound, default: Identifier) :
    NotNullTag<Identifier>(key, compound, Identifier::class, default) {

    override fun get(): Identifier {
        return id(compound.getString(key))
    }

    override fun store(value: Identifier) {
        compound.setString(key, value.toString())
    }
}

