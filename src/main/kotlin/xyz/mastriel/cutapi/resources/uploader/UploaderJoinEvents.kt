package xyz.mastriel.cutapi.resources.uploader

import net.kyori.adventure.resource.*
import org.bukkit.event.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.utils.*
import java.net.*
import java.util.*

internal class UploaderJoinEvents : Listener {

    val PACK_ID = UUID.randomUUID()

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val (packUrl, packHash) = CuTAPI.resourcePackManager.packInfo ?: return
        e.player.sendResourcePacks(
            ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI(packUrl), packHash))
                .required(true)
                .prompt("For the best experience, you must use the resource pack.".colored)
        )
    }

}