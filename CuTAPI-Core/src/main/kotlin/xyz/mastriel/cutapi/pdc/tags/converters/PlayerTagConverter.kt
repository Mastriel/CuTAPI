package xyz.mastriel.cutapi.pdc.tags.converters

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.*

object PlayerTagConverter : TagConverter<String, OfflinePlayer>(String::class, OfflinePlayer::class) {

    override fun fromPrimitive(primitive: String): OfflinePlayer {
        return Bukkit.getOfflinePlayer(UUID.fromString(primitive))
    }

    override fun toPrimitive(complex: OfflinePlayer): String {
        return complex.uniqueId.toString()
    }


}