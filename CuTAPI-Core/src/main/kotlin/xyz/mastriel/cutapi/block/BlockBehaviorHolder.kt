package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.block.behaviors.BlockBehavior
import xyz.mastriel.cutapi.items.CustomItem
import xyz.mastriel.cutapi.items.behaviors.ItemBehavior
import kotlin.reflect.KClass

private class BlockBehaviorHolder(block: CustomBlock) /*: BehaviorHolder<BlockBehavior>*/ {

    /*
    private val behaviors = block.descriptor.itemBehaviors

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

    override fun getAllBehaviors(): Set<BlockBehavior> {
        return behaviors.toSet()
    }
    */
}

// fun blockBehaviorHolder(item: CustomBlock) : BehaviorHolder<ItemBehavior> = BlockBehaviorHolder(block)