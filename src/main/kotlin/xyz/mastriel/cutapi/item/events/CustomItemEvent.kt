package xyz.mastriel.cutapi.item.events

import org.bukkit.event.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.behaviors.*


public abstract class CustomItemEvent(public open val item: CuTItemStack) : Event(),
    BehaviorHolder<ItemBehavior> by item.type {

    public val behaviors: Set<ItemBehavior> get() = item.getAllBehaviors()
    public inline fun <reified T : ItemBehavior> getBehavior(): T = item.getBehavior(T::class)
    public inline fun <reified T : ItemBehavior> getBehaviorOrNull(): T? = item.getBehaviorOrNull(T::class)
    public inline fun <reified T : ItemBehavior> hasBehavior(): Boolean = item.hasBehavior(T::class)


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
}