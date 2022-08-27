package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTContainer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class NullablePlayerTag(key: String, container: NBTContainer, default: OfflinePlayer?) :
    NullableTag<OfflinePlayer>(key, container, OfflinePlayer::class, default) {

    override fun get(): OfflinePlayer? {
        if (isNull()) return null
        val uuid = nbtContainer.getUUID(key) ?: return null
        return Bukkit.getOfflinePlayer(uuid)
    }

    override fun store(value: OfflinePlayer?) {
        if (value == null) return storeNull()
        nbtContainer.setUUID(key, value.uniqueId)
    }
}