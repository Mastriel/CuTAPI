package xyz.mastriel.cutapi.utils

import net.kyori.adventure.key.*
import net.kyori.adventure.sound.*
import org.bukkit.entity.*


public fun sound(key: String, volume: Float, pitch: Float = 1.0f): Sound {
    return Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch)
}

public fun LivingEntity.playSound(key: String, volume: Float, pitch: Float = 1.0f) {
    playSound(sound(key, volume, pitch))
}