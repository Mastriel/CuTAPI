package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTContainer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class NotNullPlayerTag(key: String, container: NBTContainer, default: OfflinePlayer) :
    NotNullTag<OfflinePlayer>(key, container, OfflinePlayer::class, default) {

    override fun get(): OfflinePlayer {
        val uuid = nbtContainer.getUUID(key) ?: error("Invalid UUID")
        return Bukkit.getOfflinePlayer(uuid)
    }

    override fun store(value: OfflinePlayer) {
        nbtContainer.setUUID(key, value.uniqueId)
    }
}