package xyz.mastriel.cutapi.resources.uploader

import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.utils.colored
import java.net.URI
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