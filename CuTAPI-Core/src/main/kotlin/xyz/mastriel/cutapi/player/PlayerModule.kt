package xyz.mastriel.cutapi.player

import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*


/**
 * A player module contains data about a player that persists while online. An instance of this class is created
 * when a player joins the server, and is deleted when they leave.
 *
 * @see PlayerModuleManager
 */
@Deprecated("Bad design")
interface PlayerModule {

    val uuid: UUID

    val player get() = Bukkit.getPlayer(uuid) ?: error("Player $uuid is not available.")

    /**
     * Called at [PlayerJoinEvent].
     */
    fun onJoin(e: PlayerJoinEvent) {}

    /**
     * When a player leaves the server, this state is removed from this player's pool of states. This is called before
     * this state is deleted.
     */
    fun onLeave(e: PlayerQuitEvent) {}
}