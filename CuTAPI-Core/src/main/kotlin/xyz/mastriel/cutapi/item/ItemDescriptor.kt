package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.behavior.isRepeatable
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.item.behaviors.itemBehaviorHolder
import xyz.mastriel.cutapi.resourcepack.resourcetypes.TextureRef
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.Personalized
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault


@DslMarker
annotation class ItemDescriptorDsl

/**
 * A class describing a CustomMaterial. Can be extended to provide additional info
 * to specific Bukkit Material types (such as armors having durability fields)
 *
 * @see ItemDescriptorBuilder
 */
class ItemDescriptor internal constructor(
    /**
     * The name of the custom material, by default.
     *
     * @see Component
     */
    val name: PersonalizedWithDefault<Component>? = null,
    /**
     * The texture that this custom material should use, by default.
     * */
    val texture: Personalized<TextureRef>? = null,
    val description: (DescriptionBuilder.() -> Unit)? = null,

    /**
     * This is always actually a MutableList<ItemBehavior>, however you should
     * not modify this unless you know what you're doing.
     */
    val itemBehaviors: List<ItemBehavior> = mutableListOf<ItemBehavior>()
) {

    infix fun with(block: ItemDescriptorBuilder.() -> Unit) : ItemDescriptor {
        val other = ItemDescriptorBuilder().apply(block).build()
        return itemDescriptor {
            name = other.name ?: this@ItemDescriptor.name
            texture = other.texture ?: this@ItemDescriptor.texture
            description = other.description ?: this@ItemDescriptor.description

            itemBehaviors.addAll(this@ItemDescriptor.itemBehaviors)

            for (otherBehavior in other.itemBehaviors) {
                val behaviorCollision = itemBehaviors.any { it.id == otherBehavior.id }
                if (behaviorCollision) {
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
class ItemDescriptorBuilder internal constructor() {
    var name: PersonalizedWithDefault<Component>? = null
    var texture: Personalized<TextureRef>? = null
    var description: (DescriptionBuilder.() -> Unit)? = {
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
            name = name,
            description = description,
            texture = texture,
            itemBehaviors = itemBehaviors
        )
    }

    fun description(block: DescriptionBuilder.() -> Unit) {
        description = block
    }

    fun noDescription() {
        description = {}
    }
}

/**
 * A class for creating dynamic descriptions for [CuTItemStack]s.
 * Whenever the server sends an ItemStack to the client for any reason, this will be
 * applied to it.
 */
@ItemDescriptorDsl
open class DescriptionBuilder(val itemStack: CuTItemStack, val viewer: Player?) :
    BehaviorHolder<ItemBehavior> by itemBehaviorHolder(itemStack.type) {

    val type get() = itemStack.type

    private val lines = mutableListOf<Component>()

    /**
     * Adds a Component to this item description.
     *
     * @param component
     */
    fun textComponent(component: Component) {
        lines += component
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

