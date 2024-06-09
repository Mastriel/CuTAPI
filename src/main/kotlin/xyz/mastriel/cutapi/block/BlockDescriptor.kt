package xyz.mastriel.cutapi.block

import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.block.behaviors.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import xyz.mastriel.cutapi.utils.personalized.*


public sealed interface TileDescriptor {
    public val behaviors: List<TileBehavior>
    public val blockStrategy: BlockStrategy
    public val name: Personalized<Component>?
    public val itemPolicy: BlockItemPolicy
}


public class BlockDescriptor(
    override val behaviors: List<BlockBehavior> = mutableListOf(),
    override val blockStrategy: BlockStrategy,
    override val name: Personalized<Component>?,
    override val itemPolicy: BlockItemPolicy
) : TileDescriptor

public class TileEntityDescriptor(
    override val behaviors: List<TileEntityBehavior> = mutableListOf(),
    override val blockStrategy: BlockStrategy,
    override val name: Personalized<Component>?,
    override val itemPolicy: BlockItemPolicy
) : TileDescriptor

public abstract class TileDescriptorBuilder<B : TileBehavior, T : TileDescriptor, C : CustomTile<*>> {

    public open val behaviors: ListBehaviorHolder<B> = ListBehaviorHolder<B>()

    public open val onRegister: EventHandlerList<C> = EventHandlerList<C>()

    public open var blockStrategy: BlockStrategy = BlockStrategy.Mushroom
    public open var itemPolicy: BlockItemPolicy = BlockItemPolicy.Generate {

    }

    public open var name: Personalized<Component>? = null

    public open fun behavior(vararg behaviors: B) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)
            this.behaviors += behavior
        }
    }

    public open fun behavior(behaviors: Collection<B>) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)
            this.behaviors += behavior
        }
    }


    public abstract fun build(): T

}


public class BlockDescriptorBuilder : TileDescriptorBuilder<BlockBehavior, BlockDescriptor, CustomBlock<*>>() {
    override fun build(): BlockDescriptor {
        return BlockDescriptor(behaviors, blockStrategy, name, itemPolicy)
    }
}

private fun BlockBehavior.tileEntity(): TileEntityBehavior {
    return object : TileEntityBehavior(this.id), TileBehavior by this {
        override val id: Identifier
            get() = super.id

    }
}

public class TileEntityDescriptorBuilder :
    TileDescriptorBuilder<TileEntityBehavior, TileEntityDescriptor, CustomTileEntity<*>>() {

    @JvmName("tileBehavior")
    public fun behavior(vararg behaviors: TileBehavior) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)

            when (behavior) {
                is TileEntityBehavior -> {
                    this.behaviors += behavior
                }

                is BlockBehavior -> {
                    this.behaviors += behavior.tileEntity()
                }
            }

        }
    }

    @JvmName("blockBehavior")
    public fun behavior(behaviors: Collection<TileBehavior>) {
        behavior(*behaviors.toTypedArray())
    }

    override fun build(): TileEntityDescriptor {
        return TileEntityDescriptor(behaviors, blockStrategy, name, itemPolicy)
    }
}

public fun blockDescriptor(block: BlockDescriptorBuilder.() -> Unit): BlockDescriptor {
    return BlockDescriptorBuilder().apply(block).build()
}

public fun tileEntityDescriptor(block: TileEntityDescriptorBuilder.() -> Unit): TileEntityDescriptor {
    return TileEntityDescriptorBuilder().apply(block).build()
}