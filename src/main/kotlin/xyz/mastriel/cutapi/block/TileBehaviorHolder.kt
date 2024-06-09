package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.block.behaviors.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

private sealed class TileBehaviorHolder<T : TileBehavior>(block: CustomTile<*>) : BehaviorHolder<T> {

    @Suppress("UNCHECKED_CAST")
    private val behaviors = block.descriptor.behaviors as List<T>

    override fun hasBehavior(behavior: KClass<out T>): Boolean {
        return getBehaviorOrNull(behavior) != null
    }

    override fun <T2 : T> getBehavior(behavior: KClass<T2>): T2 {
        return getBehaviorOrNull(behavior) ?: error("Behavior ${behavior.qualifiedName} not found in ${this}!")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T2 : T> getBehaviorOrNull(behavior: KClass<T2>): T2? {
        return behaviors.find { it::class == behavior } as? T2?
    }

    override fun getAllBehaviors(): Set<T> {
        return behaviors.toSet()
    }

    override fun hasBehavior(behaviorId: Identifier): Boolean {
        return behaviors.any { it.id == behaviorId }
    }

    override fun <T2 : T> getBehavior(behaviorId: Identifier): T2 {
        return getBehaviorOrNull(behaviorId) ?: error("Behavior $behaviorId doesn't exist on this block.")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T2 : T> getBehaviorOrNull(behaviorId: Identifier): T2? {
        return behaviors.find { it.id == behaviorId } as? T2?
    }

    class Block(block: CustomBlock<*>) : TileBehaviorHolder<BlockBehavior>(block)
    class TileEntity(tileEntity: CustomTileEntity<*>) : TileBehaviorHolder<TileEntityBehavior>(tileEntity)
}


public fun blockBehaviorHolder(block: CustomBlock<*>): BehaviorHolder<BlockBehavior> = TileBehaviorHolder.Block(block)
public fun tileEntityBehaviorHolder(tileEntity: CustomTileEntity<*>): BehaviorHolder<TileEntityBehavior> =
    TileBehaviorHolder.TileEntity(tileEntity)
