package xyz.mastriel.cutapi.nbt.tags.notnull

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.registry.id

class NotNullCustomMaterialTag(key: String, compound: NBTCompound, default: CustomMaterial) :
    NotNullTag<CustomMaterial>(key, compound, CustomMaterial::class, default) {

    override fun get(): CustomMaterial {
        return CustomMaterial.get(id(compound.getString(key)))
    }

    override fun store(value: CustomMaterial) {
        compound.setString(key, value.id.toString())
    }
}

