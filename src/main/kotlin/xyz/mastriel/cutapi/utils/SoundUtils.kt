package xyz.mastriel.cutapi.utils

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.LivingEntity


fun sound(key: String, volume: Float, pitch: Float = 1.0f) : Sound {
    return Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch)
}

fun LivingEntity.playSound(key: String, volume: Float, pitch: Float = 1.0f) {
    playSound(sound(key, volume, pitch))
}