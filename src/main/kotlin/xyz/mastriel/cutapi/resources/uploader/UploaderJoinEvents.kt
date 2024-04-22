package xyz.mastriel.cutapi.resourcepack.uploader

import org.bukkit.event.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.utils.*

internal class UploaderJoinEvents : Listener {

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val (packUrl, packHash) = CuTAPI.resourcePackManager.packInfo ?: return
        println(packHash)
        e.player.setResourcePack(packUrl, packHash)
    }

}