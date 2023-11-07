package xyz.mastriel.cutapi.block

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.behavior.ListBehaviorHolder
import xyz.mastriel.cutapi.behavior.requireRepeatableIfExists
import xyz.mastriel.cutapi.block.behaviors.BlockBehavior
import xyz.mastriel.cutapi.block.behaviors.TileBehavior
import xyz.mastriel.cutapi.block.behaviors.TileEntityBehavior
import xyz.mastriel.cutapi.utils.personalized.Personalized
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault


sealed interface TileDescriptor {
    val behaviors: List<TileBehavior>
    val blockStrategy: BlockStrategy
    val name: PersonalizedWithDefault<Component>
}


class BlockDescriptor(
    override val behaviors: List<BlockBehavior> = mutableListOf(),
    override val blockStrategy: BlockStrategy
) : TileDescriptor

class TileEntityDescriptor(
    override val behaviors: List<TileEntityBehavior> = mutableListOf(),
    override val blockStrategy: BlockStrategy
) : TileDescriptor

abstract class TileDescriptorBuilder<B: TileBehavior, T: TileDescriptor> {

    open val behaviors = ListBehaviorHolder<B>()

    open var blockStrategy: BlockStrategy = BlockStrategy.Mushroom

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
        return BlockDescriptor(behaviors, blockStrategy)
    }
}

private fun BlockBehavior.adapt() : TileEntityBehavior {
    return object : TileEntityBehavior(this.id) {
        override fun onLeftClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {
            this@adapt.onLeftClick(player, block, event)
        }
        override fun onMiddleClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {
            this@adapt.onLeftClick(player, block, event)
        }
        override fun onRightClick(player: Player, block: CuTPlacedTile, event: PlayerInteractEvent) {
            this@adapt.onLeftClick(player, block, event)
        }
    }
}

class TileEntityDescriptorBuilder : TileDescriptorBuilder<TileEntityBehavior, TileEntityDescriptor>() {

    fun behavior(vararg behaviors: BlockBehavior) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)
            this.behaviors += behavior.adapt()
        }
    }

    fun behavior(behaviors: Collection<BlockBehavior>) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)
            this.behaviors += behavior.adapt()
        }
    }

    override fun build() : TileEntityDescriptor {
        return TileEntityDescriptor(behaviors, blockStrategy)
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