package xyz.mastriel.cutapi.nbt.tags.nullable

import de.tr7zw.changeme.nbtapi.NBTCompound
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class NullablePlayerTag(key: String, compound: NBTCompound, default: OfflinePlayer?) :
    NullableTag<OfflinePlayer>(key, compound, OfflinePlayer::class, default) {

    override fun get(): OfflinePlayer? {
        if (isNull()) return null
        val uuid = compound.getUUID(key) ?: return null
        return Bukkit.getOfflinePlayer(uuid)
    }

    override fun store(value: OfflinePlayer?) {
        if (value == null) return storeNull()
        compound.setUUID(key, value.uniqueId)
    }
}