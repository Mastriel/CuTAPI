package xyz.mastriel.cutapi.nbt.tags.notnull

import de.tr7zw.changeme.nbtapi.NBTCompound
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class NotNullPlayerTag(key: String, compound: NBTCompound, default: OfflinePlayer) :
    NotNullTag<OfflinePlayer>(key, compound, OfflinePlayer::class, default) {

    override fun get(): OfflinePlayer {
        val uuid = compound.getUUID(key) ?: return default
        return Bukkit.getOfflinePlayer(uuid)
    }

    override fun store(value: OfflinePlayer) {
        compound.setUUID(key, value.uniqueId)
    }
}