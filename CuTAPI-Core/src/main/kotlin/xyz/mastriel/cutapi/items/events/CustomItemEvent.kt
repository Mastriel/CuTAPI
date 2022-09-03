package xyz.mastriel.cutapi.items.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.components.MaterialComponent
import xyz.mastriel.cutapi.items.components.getComponent
import xyz.mastriel.cutapi.items.components.getComponentOrNull
import xyz.mastriel.cutapi.items.components.hasComponent

abstract class CustomItemEvent(open val item: CuTItemStack) : Event() {
    val components get() = item.getAllComponents()
    inline fun <reified T : MaterialComponent> getComponent() = item.getComponent<T>()
    inline fun <reified T : MaterialComponent> getComponentOrNull() = item.getComponentOrNull<T>()
    inline fun <reified T : MaterialComponent> hasComponent() = item.hasComponent<T>()


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