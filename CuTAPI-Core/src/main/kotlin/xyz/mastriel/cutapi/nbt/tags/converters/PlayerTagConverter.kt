package xyz.mastriel.cutapi.nbt.tags.converters

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.*

object PlayerTagConverter : TagConverter<UUID, OfflinePlayer>(UUID::class, OfflinePlayer::class) {

    override fun fromPrimitive(primitive: UUID): OfflinePlayer {
        return Bukkit.getOfflinePlayer(primitive)
    }

    override fun toPrimitive(complex: OfflinePlayer): UUID {
        return complex.uniqueId
    }


}