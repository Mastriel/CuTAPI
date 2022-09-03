package xyz.mastriel.cutapi.items.events

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import xyz.mastriel.cutapi.items.CuTItemStack

data class CustomItemObtainEvent(override val item: CuTItemStack, val player: Player) : CustomItemEvent(item), Cancellable {

    private var cancel : Boolean = false

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun isCancelled(): Boolean {
        return cancel
    }

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }
}