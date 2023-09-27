package xyz.mastriel.cutapi.player

import org.bukkit.event.player.AsyncPlayerPreLoginEvent

@Deprecated("Bad design")
fun interface PreLoginHandler<M: PlayerModule> {

    /**
     * This is run async!
     *
     * @see AsyncPlayerPreLoginEvent
     */
    fun login(e: AsyncPlayerPreLoginEvent) : M
}