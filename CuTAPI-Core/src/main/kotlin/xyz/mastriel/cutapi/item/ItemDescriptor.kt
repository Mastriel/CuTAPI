package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.behavior.isRepeatable
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.item.behaviors.itemBehaviorHolder
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.colored


@DslMarker
annotation class ItemDescriptorDsl

/**
 * A class describing a CustomMaterial. Can be extended to provide additional info
 * to specific Bukkit Material types (such as armors having durability fields)
 *
 * @see ItemDescriptorBuilder
 */
class ItemDescriptor internal constructor(
    val display: (ItemDisplayBuilder.() -> Unit)? = null,

    /**
     * This is always actually a MutableList<ItemBehavior>, however you should
     * not modify this unless you know what you're doing.
     */
    val itemBehaviors: List<ItemBehavior> = mutableListOf()
) {

    infix fun with(block: ItemDescriptorBuilder.() -> Unit) : ItemDescriptor {
        val other = ItemDescriptorBuilder().apply(block).build()
        return this + other
    }

    operator fun plus(other: ItemDescriptor) : ItemDescriptor {
        return itemDescriptor {
            display = other.display ?: this@ItemDescriptor.display

            itemBehaviors.addAll(this@ItemDescriptor.itemBehaviors)

            for (otherBehavior in other.itemBehaviors) {
                val behaviorCollision = itemBehaviors.any { it.id == otherBehavior.id }
                if (behaviorCollision && !otherBehavior.isRepeatable()) {
                    itemBehaviors.removeIf { it.id == otherBehavior.id }
                }
                behavior(otherBehavior)
            }
        }
    }
}

/**
 * A builder for the [ItemDescriptor], containing some useful functions to make creating
 * resources much easier.
 *
 * @see ItemDescriptor
 */
@ItemDescriptorDsl
class ItemDescriptorBuilder {
    var display: (ItemDisplayBuilder.() -> Unit)? = {
        emptyLine()
        behaviorLore(Color.Blue)
    }

    val itemBehaviors = mutableListOf<ItemBehavior>()

    fun behavior(vararg behaviors: ItemBehavior) {
        for (behavior in behaviors) {
            if (this.itemBehaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
            this.itemBehaviors += behavior
        }
    }

    fun behavior(behaviors: Collection<ItemBehavior>) {
        for (behavior in behaviors) {
            if (this.itemBehaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
            this.itemBehaviors += behavior
        }
    }

    fun build(): ItemDescriptor {
        return ItemDescriptor(
            display = display,
            itemBehaviors = itemBehaviors
        )
    }

    fun display(block: ItemDisplayBuilder.() -> Unit) {
        display = block
    }

    fun noDisplay() {
        display = {}
    }
}

/**
 * A class for creating dynamic displays (lore, name, etc.) for [CuTItemStack]s.
 * Whenever the server sends an ItemStack to the client for any reason, this will be
 * applied to it.
 */
@ItemDescriptorDsl
open class ItemDisplayBuilder(val itemStack: CuTItemStack, val viewer: Player?) :
    BehaviorHolder<ItemBehavior> by itemBehaviorHolder(itemStack.type) {

    val type get() = itemStack.type

    private val lines = mutableListOf<Component>()
    var name : Component? = null

    var texture: ResourceRef<Texture2D>? = null

    /**
     * Adds a Component to this item description.
     *
     * @param components The component(s) being added to this item's description.
     */
    fun text(vararg components: Component) {
        lines += components
    }

    fun text(components: Collection<Component>) {
        lines += components
    }

    /**
     * Adds an empty line to this item description.
     */
    fun emptyLine() {
        lines += "&7".colored
    }

    /**
     * Adds any lore that any [ItemBehavior] would want to implement.
     */
    fun behaviorLore(mainColor: Color) {
        for (component in itemStack.getAllBehaviors()) {
            val lore = component.getLore(itemStack, viewer)
            if (lore != null) {
                lines.add(lore.color(mainColor.textColor))
            }
        }
    }

    fun toTextComponents(): List<Component> {
        return lines.toList()
    }

    inline fun <reified B: ItemBehavior> hasBehavior() = hasBehavior(B::class)
    inline fun <reified B: ItemBehavior> getBehavior() = getBehavior(B::class)
    inline fun <reified B: ItemBehavior> getBehaviorOrNull() = getBehaviorOrNull(B::class)

}

/**
 * A descriptor for a [xyz.mastriel.cutapi.item.CustomItem], which contains useful info
 * such as a name, lore, NBT, etc.
 *
 * @param block The builder, used to create the final [ItemDescriptor]
 */
fun itemDescriptor(block: ItemDescriptorBuilder.() -> Unit) =
    ItemDescriptorBuilder().apply(block).build()

/**
 * A default descriptor for a [xyz.mastriel.cutapi.item.CustomItem].
 */
fun defaultItemDescriptor() =
    ItemDescriptorBuilder().build()

