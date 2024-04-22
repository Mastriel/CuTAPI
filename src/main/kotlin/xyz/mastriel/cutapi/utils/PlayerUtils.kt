package xyz.mastriel.cutapi.utils

import org.bukkit.Bukkit
import org.bukkit.entity.Player


fun onlinePlayers() = Bukkit.getOnlinePlayers()
    .filterNotNull()
    .toList()

fun playerNameList() = onlinePlayers().map(Player::getName)
