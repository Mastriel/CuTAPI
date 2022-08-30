package xyz.mastriel.cutapi.items.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.components.ItemComponent

abstract class CustomItemEvent(open val item: CuTItemStack) : Event() {
    val components get() = item.components
    inline fun <reified T : ItemComponent> getComponent() = item.getComponent<T>()
    inline fun <reified T : ItemComponent> getComponentOrNull() = item.getComponentOrNull<T>()
    inline fun <reified T : ItemComponent> hasComponent() = item.hasComponent<T>()


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
}