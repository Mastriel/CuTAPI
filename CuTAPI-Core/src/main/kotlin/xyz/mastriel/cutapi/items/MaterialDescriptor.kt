package xyz.mastriel.cutapi.items

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.items.behaviors.MaterialBehavior
import xyz.mastriel.cutapi.items.behaviors.isRepeatable
import xyz.mastriel.cutapi.items.behaviors.materialBehaviorHolder
import xyz.mastriel.cutapi.resourcepack.Texture
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.colored


@DslMarker
annotation class MaterialDescriptorDsl

/**
 * A class describing a CustomMaterial. Can be extended to provide additional info
 * to specific Bukkit Material types (such as armors having durability fields)
 *
 * @see MaterialDescriptorBuilder
 */
open class MaterialDescriptor internal constructor(
    /**
     * The name of the custom material, by default.
     *
     * @see Component
     */
    open val name: Component? = null,
    /**
     * The texture that this custom material should use, by default.
     * */
    open val texture: Texture? = null,
    open val description: (DescriptionBuilder.() -> Unit)? = null,
    open val behaviors: List<MaterialBehavior> = listOf()
) {
    infix fun with(block: MaterialDescriptorBuilder.() -> Unit) : MaterialDescriptor {
        val other = MaterialDescriptorBuilder().apply(block).build()
        return materialDescriptor {
            name = other.name ?: this@MaterialDescriptor.name
            texture = other.texture ?: this@MaterialDescriptor.texture
            description = other.description ?: this@MaterialDescriptor.description

            behaviors.addAll(this@MaterialDescriptor.behaviors)

            for (otherBehavior in other.behaviors) {
                val behaviorCollision = behaviors.any { it.id == otherBehavior.id }
                if (behaviorCollision) {
                    behaviors.removeIf { it.id == otherBehavior.id }
                }
                behavior(otherBehavior)
            }
        }

    }
}

/**
 * A builder for the [MaterialDescriptor], containing some useful functions to make creating
 * resources much easier.
 *
 * @see MaterialDescriptor
 */
@MaterialDescriptorDsl
open class MaterialDescriptorBuilder internal constructor() {
    var name: Component? = null
    var texture: Texture? = null
    var description: (DescriptionBuilder.() -> Unit)? = {
        emptyLine()
        behaviorLore(Color.Blue)
    }

    val behaviors = mutableListOf<MaterialBehavior>()

    fun behavior(vararg behaviors: MaterialBehavior) {
        for (behavior in behaviors) {
            if (this.behaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableComponent annotation to be repeatable.")
            this.behaviors += behavior
        }
    }

    fun build(): MaterialDescriptor {
        return MaterialDescriptor(
            name = name,
            description = description,
            texture = texture,
            behaviors = behaviors
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
 * Whenever the server sends an itemstack to the client for any reason, this will be
 * applied to it.
 */
@MaterialDescriptorDsl
class DescriptionBuilder(val itemStack: CuTItemStack, val viewer: Player) :
    BehaviorHolder<MaterialBehavior> by materialBehaviorHolder(itemStack.customMaterial) {

    val customMaterial get() = itemStack.customMaterial

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
     * Adds any lore that any [MaterialBehavior] would want to implement.
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

    inline fun <reified B: MaterialBehavior> hasBehavior() = hasBehavior(B::class)
    inline fun <reified B: MaterialBehavior> getBehavior() = getBehavior(B::class)
    inline fun <reified B: MaterialBehavior> getBehaviorOrNull() = getBehaviorOrNull(B::class)
}


/**
 * A descriptor for a [xyz.mastriel.cutapi.items.CustomMaterial], which contains useful info
 * such as a name, lore, NBT, etc.
 *
 * @param block The builder, used to create the final [MaterialDescriptor]
 */
fun materialDescriptor(block: MaterialDescriptorBuilder.() -> Unit) =
    MaterialDescriptorBuilder().apply(block).build()

/**
 * A default descriptor for a [xyz.mastriel.cutapi.items.CustomMaterial].
 */
fun defaultMaterialDescriptor() =
    MaterialDescriptorBuilder().build()
