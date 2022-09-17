package xyz.mastriel.cutapi.items.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.behaviors.MaterialBehavior


abstract class CustomItemEvent(open val item: CuTItemStack) : Event(),
    BehaviorHolder<MaterialBehavior> by item.customMaterial {

    val components get() = item.getAllBehaviors()
    inline fun <reified T : MaterialBehavior> getBehavior() = item.getBehavior<T>()
    inline fun <reified T : MaterialBehavior> getBehaviorOrNull() = item.getBehaviorOrNull<T>()
    inline fun <reified T : MaterialBehavior> hasBehavior() = item.hasBehavior<T>()


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