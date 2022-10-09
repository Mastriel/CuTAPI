package xyz.mastriel.cutapi.items.behaviors

import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.items.CustomItem
import kotlin.reflect.KClass

private class ItemBehaviorHolder(item: CustomItem) : BehaviorHolder<ItemBehavior> {

    private val behaviors = item.descriptor.itemBehaviors

    override fun hasBehavior(behavior: KClass<out ItemBehavior>): Boolean {
        return getBehaviorOrNull(behavior) != null
    }

    override fun <T : ItemBehavior> getBehavior(behavior: KClass<T>): T {
        return getBehaviorOrNull(behavior) ?: error("Component ${behavior.qualifiedName} not found in component list!")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ItemBehavior> getBehaviorOrNull(behavior: KClass<T>): T? {
        return behaviors.find { it::class == behavior } as? T?
    }

    override fun getAllBehaviors(): Set<ItemBehavior> {
        return behaviors.toSet()
    }
}

fun itemBehaviorHolder(item: CustomItem) : BehaviorHolder<ItemBehavior> = ItemBehaviorHolder(item)