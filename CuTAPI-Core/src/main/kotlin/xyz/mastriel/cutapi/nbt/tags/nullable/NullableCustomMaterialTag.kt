package xyz.mastriel.cutapi.nbt.tags.nullable

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.registry.id

class NullableCustomMaterialTag(key: String, compound: NBTCompound, default: CustomMaterial?) :
    NullableTag<CustomMaterial>(key, compound, CustomMaterial::class, default) {

    override fun get(): CustomMaterial? {
        if (isNull()) return null
        val string = compound.getString(key) ?: return null
        return CustomMaterial.get(id(string))
    }

    override fun store(value: CustomMaterial?) {
        if (value == null) return storeNull()
        compound.setString(key, value.id.toString())
    }
}