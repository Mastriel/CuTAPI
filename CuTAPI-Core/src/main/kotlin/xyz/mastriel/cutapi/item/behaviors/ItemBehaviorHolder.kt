package xyz.mastriel.cutapi.item.behaviors

import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.registry.Identifier
import kotlin.reflect.KClass

private class ItemBehaviorHolder(item: CustomItem) : BehaviorHolder<ItemBehavior> {

    private val behaviors = item.descriptor.itemBehaviors

    override fun hasBehavior(behavior: KClass<out ItemBehavior>): Boolean {
        return getBehaviorOrNull(behavior) != null
    }

    override fun <T : ItemBehavior> getBehavior(behavior: KClass<T>): T {
        return getBehaviorOrNull(behavior) ?: error("Behavior ${behavior.qualifiedName} doesn't exist on this item.")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ItemBehavior> getBehaviorOrNull(behavior: KClass<T>): T? {
        return behaviors.find { it::class == behavior } as? T?
    }

    override fun hasBehavior(behaviorId: Identifier): Boolean {
        return behaviors.any { it.id == behaviorId }
    }

    @Suppress("unchecked_cast")
    override fun <T : ItemBehavior> getBehaviorOrNull(behaviorId: Identifier): T? {
        return behaviors.find { it.id == behaviorId } as? T?
    }

    override fun <T : ItemBehavior> getBehavior(behaviorId: Identifier): T {
        return getBehaviorOrNull(behaviorId) ?: error("Behavior $behaviorId doesn't exist on this item.")
    }

    override fun getAllBehaviors(): Set<ItemBehavior> {
        return behaviors.toSet()
    }
}

fun itemBehaviorHolder(item: CustomItem) : BehaviorHolder<ItemBehavior> = ItemBehaviorHolder(item)