package xyz.mastriel.cutapi.block

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.behavior.ListBehaviorHolder
import xyz.mastriel.cutapi.behavior.requireRepeatableIfExists
import xyz.mastriel.cutapi.block.behaviors.BlockBehavior
import xyz.mastriel.cutapi.block.behaviors.TileBehavior
import xyz.mastriel.cutapi.block.behaviors.TileEntityBehavior
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault


sealed interface TileDescriptor {
    val behaviors: List<TileBehavior>
    val blockStrategy: BlockStrategy
    val name: PersonalizedWithDefault<Component>?

}


class BlockDescriptor(
    override val behaviors: List<BlockBehavior> = mutableListOf(),
    override val blockStrategy: BlockStrategy,
    override val name: PersonalizedWithDefault<Component>?
) : TileDescriptor

class TileEntityDescriptor(
    override val behaviors: List<TileEntityBehavior> = mutableListOf(),
    override val blockStrategy: BlockStrategy,
    override val name: PersonalizedWithDefault<Component>?
) : TileDescriptor

abstract class TileDescriptorBuilder<B: TileBehavior, T: TileDescriptor> {

    open val behaviors = ListBehaviorHolder<B>()

    open var blockStrategy: BlockStrategy = BlockStrategy.Mushroom

    open var name: PersonalizedWithDefault<Component>? = null

    open fun behavior(vararg behaviors: B) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)
            this.behaviors += behavior
        }
    }

    open fun behavior(behaviors: Collection<B>) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)
            this.behaviors += behavior
        }
    }


    abstract fun build() : T

}


class BlockDescriptorBuilder : TileDescriptorBuilder<BlockBehavior, BlockDescriptor>() {
    override fun build() : BlockDescriptor {
        return BlockDescriptor(behaviors, blockStrategy, name)
    }
}

private fun BlockBehavior.tileEntity() : TileEntityBehavior {
    return object : TileEntityBehavior(this.id), TileBehavior by this {
        override val id: Identifier
            get() = super.id

    }
}

class TileEntityDescriptorBuilder : TileDescriptorBuilder<TileEntityBehavior, TileEntityDescriptor>() {

    @JvmName("tileBehavior")
    fun behavior(vararg behaviors: TileBehavior) {
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
    fun behavior(behaviors: Collection<TileBehavior>) {
        behavior(*behaviors.toTypedArray())
    }

    override fun build() : TileEntityDescriptor {
        return TileEntityDescriptor(behaviors, blockStrategy, name)
    }
}

open class BlockDisplayBuilder(val tile: CuTPlacedTile, val viewer: Player) {
    var name : Component? = null

}


fun blockDescriptor(block: BlockDescriptorBuilder.() -> Unit) : BlockDescriptor {
    return BlockDescriptorBuilder().apply(block).build()
}

fun tileEntityDescriptor(block: TileEntityDescriptorBuilder.() -> Unit) : TileEntityDescriptor {
    return TileEntityDescriptorBuilder().apply(block).build()
}