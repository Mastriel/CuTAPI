package xyz.mastriel.cutapi.utils

import org.bukkit.*
import org.bukkit.entity.*


public fun onlinePlayers(): List<Player> = Bukkit.getOnlinePlayers()
    .filterNotNull()
    .toList()

public fun playerNameList(): List<String> = onlinePlayers().map(Player::getName)
