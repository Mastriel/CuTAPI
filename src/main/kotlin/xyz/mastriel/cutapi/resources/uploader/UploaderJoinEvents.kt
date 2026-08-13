package xyz.mastriel.cutapi.resources.uploader

import io.papermc.paper.connection.PlayerConfigurationConnection
import io.papermc.paper.connection.PlayerLoginConnection
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent
import net.kyori.adventure.resource.*
import org.bukkit.event.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.utils.*
import java.net.*
import java.util.*

internal class UploaderJoinEvents : Listener {

    private val packId: UUID = UUID.randomUUID()
    private val connectionHosts: MutableMap<UUID, String> = mutableMapOf()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onLogin(e: PlayerConnectionValidateLoginEvent) {
        val profileId = when (val connection = e.connection) {
            is PlayerConfigurationConnection -> connection.profile.id
            is PlayerLoginConnection ->
                (connection.authenticatedProfile ?: connection.unsafeProfile)?.id

            else -> null
        } ?: return

        val connectionHost = e.connection.virtualHost?.hostString
        if (e.isAllowed && !connectionHost.isNullOrBlank()) {
            connectionHosts[profileId] = connectionHost
        } else {
            connectionHosts.remove(profileId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(e: PlayerJoinEvent) {
        val connectionHost = connectionHosts.remove(e.player.uniqueId)
        val (packUrl, packHash) = CuTAPI.resourcePackManager
            .packInfoForConnection(connectionHost)
            ?: return
        Plugin.info("Sending resource pack to ${e.player.name} from $packUrl")
        e.player.sendResourcePacks(
            ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(packId, URI(packUrl), packHash))
                .required(true)
                .prompt("For the best experience, you must use the resource pack.".colored)
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onResourcePackStatus(e: PlayerResourcePackStatusEvent) {
        if (e.id != packId) return
        val status = e.status.name.lowercase().replace('_', ' ')
        when (e.status) {
            PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD,
            PlayerResourcePackStatusEvent.Status.INVALID_URL,
            PlayerResourcePackStatusEvent.Status.FAILED_RELOAD,
            -> Plugin.warn("Resource pack $status for ${e.player.name}.")

            PlayerResourcePackStatusEvent.Status.DECLINED,
            PlayerResourcePackStatusEvent.Status.DISCARDED,
            -> Plugin.warn("Resource pack $status by ${e.player.name}.")

            else -> Plugin.info("Resource pack $status by ${e.player.name}.")
        }
    }

}
