package xyz.mastriel.cutapi.items.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.behaviors.ItemBehavior


abstract class CustomItemEvent(open val item: CuTItemStack) : Event(),
    BehaviorHolder<ItemBehavior> by item.type {

    val behaviors get() = item.getAllBehaviors()
    inline fun <reified T : ItemBehavior> getBehavior() = item.getBehavior(T::class)
    inline fun <reified T : ItemBehavior> getBehaviorOrNull() = item.getBehaviorOrNull(T::class)
    inline fun <reified T : ItemBehavior> hasBehavior() = item.hasBehavior(T::class)


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