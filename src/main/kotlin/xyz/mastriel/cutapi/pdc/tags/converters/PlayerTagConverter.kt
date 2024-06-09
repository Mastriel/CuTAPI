package xyz.mastriel.cutapi.pdc.tags.converters

import org.bukkit.*
import java.util.*

public object PlayerTagConverter : TagConverter<String, OfflinePlayer>(String::class, OfflinePlayer::class) {

    override fun fromPrimitive(primitive: String): OfflinePlayer {
        return Bukkit.getOfflinePlayer(UUID.fromString(primitive))
    }

    override fun toPrimitive(complex: OfflinePlayer): String {
        return complex.uniqueId.toString()
    }


}