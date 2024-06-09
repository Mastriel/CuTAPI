package xyz.mastriel.cutapi.item.events

import org.bukkit.entity.*
import org.bukkit.event.*
import xyz.mastriel.cutapi.item.*

public data class CustomItemObtainEvent(override val item: CuTItemStack, val player: Player) : CustomItemEvent(item), Cancellable {

    private var cancel : Boolean = false
    var destroyItem : Boolean = false

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList {
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