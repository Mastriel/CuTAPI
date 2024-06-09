package xyz.mastriel.cutapi.nms

import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.player.*

/**
 * Manages packet handlers for all players. This is used to inject packet handlers into the player's pipeline.
 * @see PacketEventHandler
 */
@UsesNMS
internal class PlayerPacketManager : Listener {

    private val packetHandlers = mutableMapOf<Player, PacketEventHandler>()


    /**
     * Registers a player to the packet manager. This will inject a packet event handler into the player's pipeline.
     * @param player The player to register
     */
    fun registerPlayer(player: Player) {
        val handler = PacketEventHandler(player)
        xyz.mastriel.cutapi.Plugin.info("Registered: ${player.nms().packetPipeline()}")
        player.nms().packetPipeline().addBefore("packet_handler", "cutapi_packets", handler)

        packetHandlers[player] = handler
    }

    /**
     * Removes a player from the packet manager. This will remove the packet event handler from the player's pipeline.
     */
    fun removePlayer(player: Player) {
        val handler = packetHandlers.remove(player) ?: return
        try {
            player.nms().packetPipeline().remove(handler)
        } catch (_: NoSuchElementException) {}
    }

    /**
     * Gets the packet event handler for a player. If the player is not registered, an exception will be thrown.
     * @param player The player to get the packet event handler for
     * @return The packet handler
     */
    fun getPacketHandler(player: Player) = getPacketHandlerOrNull(player) ?: throw IllegalStateException("Player not registered")

    /**
     * Gets the packet event handler for a player. If the player is not registered, null will be returned.
     * @param player The player to get the packet event handler for
     * @return The packet handler, or null if the player is not registered
     */
    fun getPacketHandlerOrNull(player: Player) = packetHandlers[player]

    /**
     * Checks if a player is registered to this.
     * @param player The player to check
     * @return true if the player is registered, false otherwise
     */
    fun isRegistered(player: Player) = packetHandlers.containsKey(player)

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) = removePlayer(e.player)

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) = registerPlayer(e.player)
}