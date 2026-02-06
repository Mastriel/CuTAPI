package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.behaviors.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*


@DslMarker
public annotation class ItemDescriptorDsl

/**
 * A class describing a CustomMaterial. Can be extended to provide additional info
 * to specific Bukkit Material types (such as armors having durability fields)
 *
 * @see ItemDescriptorBuilder
 */
public class ItemDescriptor internal constructor(
    public val display: (ItemDisplayBuilder.() -> Unit)? = null,

    /**
     * This is always actually a MutableList<ItemBehavior>, however you should
     * not modify this unless you know what you're doing.
     */
    public val itemBehaviors: List<ItemBehavior> = mutableListOf(),
    public val onRegister: EventHandlerList<ItemRegisterEvent> = EventHandlerList()
) {

    public infix fun with(block: ItemDescriptorBuilder.() -> Unit): ItemDescriptor {
        val other = ItemDescriptorBuilder().apply(block).build()
        return this + other
    }

    public operator fun plus(other: ItemDescriptor): ItemDescriptor {
        return itemDescriptor {
            display = other.display ?: this@ItemDescriptor.display

            val itemBehaviors = this@ItemDescriptor.itemBehaviors.toMutableList()

            for (otherBehavior in other.itemBehaviors) {
                val behaviorCollision = itemBehaviors.any { it.id == otherBehavior.id }
                if (behaviorCollision && !otherBehavior.isRepeatable()) {
                    itemBehaviors.removeIf { it.id == otherBehavior.id }
                }
                behavior(otherBehavior)
            }
            behavior(itemBehaviors)
        }
    }

}

public data class ItemRegisterEvent(val item: CustomItem<*>)

/**
 * A builder for the [ItemDescriptor], containing some useful functions to make creating
 * resources much easier.
 *
 * @see ItemDescriptor
 */
@ItemDescriptorDsl
public class ItemDescriptorBuilder {
    public var display: (ItemDisplayBuilder.() -> Unit)? = {
        emptyLine()
        behaviorLore(Color.Blue)
    }

    private val _itemBehaviors = mutableListOf<ItemBehavior>()
    public val itemBehaviors: List<ItemBehavior> get() = _itemBehaviors

    public val onRegister: EventHandlerList<ItemRegisterEvent> = EventHandlerList()

    public fun behavior(vararg behaviors: ItemBehavior) {
        for (behavior in behaviors) {
            if (this._itemBehaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
            _itemBehaviors.add(behavior)
            with(behavior) {
                modifyDescriptor()
            }
        }
    }

    public fun behavior(behaviors: Collection<ItemBehavior>) {
        behavior(*behaviors.toTypedArray())
    }

    public fun build(): ItemDescriptor {

        return ItemDescriptor(
            display = display,
            itemBehaviors = _itemBehaviors,
            onRegister = onRegister
        )
    }

    public fun display(block: ItemDisplayBuilder.() -> Unit) {
        display = block
    }


    public fun noDisplay() {
        display = {}
    }
}

public sealed class ItemTexture {

    public abstract fun isAvailable(): Boolean
    public abstract fun getRef(): ResourceRef<*>
    public abstract fun getItemModelId(): Identifier
    public abstract val showSwapAnimation: Boolean

    public data class Texture(val texture: ResourceRef<Texture2D>, override val showSwapAnimation: Boolean) :
        ItemTexture() {

        override fun getItemModelId(): Identifier {
            val id = texture.getResource()?.getItemModel()?.toIdentifier() ?: unknownID()
            val swapMode = if (showSwapAnimation) "__swap" else "__noswap"
            return id("${id}${swapMode}")
        }

        override fun isAvailable(): Boolean = texture.isAvailable()

        override fun getRef(): ResourceRef<*> {
            return texture
        }
    }

    public data class Model(val model: ResourceRef<Model3D>, override val showSwapAnimation: Boolean) : ItemTexture() {

        override fun getItemModelId(): Identifier {
            val id = model.getResource()?.getItemModel()?.toIdentifier() ?: unknownID()
            val swapMode = if (showSwapAnimation) "__swap" else "__noswap"
            return id("${id}${swapMode}")
        }

        override fun isAvailable(): Boolean = model.isAvailable()

        override fun getRef(): ResourceRef<*> {
            return model
        }
    }
}

public fun itemTexture(texture: ResourceRef<Texture2D>, showSwapAnimation: Boolean = true): ItemTexture.Texture =
    ItemTexture.Texture(texture, showSwapAnimation)

public fun itemModel(model: ResourceRef<Model3D>, showSwapAnimation: Boolean = true): ItemTexture.Model =
    ItemTexture.Model(model, showSwapAnimation)

public fun itemTexture(plugin: CuTPlugin, path: String, showSwapAnimation: Boolean = true): ItemTexture.Texture =
    ItemTexture.Texture(ref(plugin, path), showSwapAnimation)

public fun itemModel(plugin: CuTPlugin, path: String, showSwapAnimation: Boolean = true): ItemTexture.Model =
    ItemTexture.Model(ref(plugin, path), showSwapAnimation)

public fun itemTexture(stringPath: String, showSwapAnimation: Boolean = true): ItemTexture.Texture =
    ItemTexture.Texture(ref(stringPath), showSwapAnimation)

public fun itemModel(stringPath: String, showSwapAnimation: Boolean = true): ItemTexture.Model =
    ItemTexture.Model(ref(stringPath), showSwapAnimation)

/**
 * A class for creating dynamic displays (lore, name, etc.) for [CuTItemStack]s.
 * Whenever the server sends an ItemStack to the client for any reason, this will be
 * applied to it.
 */
@ItemDescriptorDsl
public open class ItemDisplayBuilder(public val itemStack: CuTItemStack, public val viewer: Player?) :
    BehaviorHolder<ItemBehavior> by itemBehaviorHolder(itemStack.type) {

    public val type: CustomItem<*> get() = itemStack.type

    private val lines = mutableListOf<Component>()
    public var name: Component? = null

    public var texture: ItemTexture? = null

    /**
     * Adds a Component to this item description.
     *
     * @param components The component(s) being added to this item's description.
     */
    public fun text(vararg components: Component) {
        lines += components
    }

    public fun text(components: Collection<Component>) {
        lines += components
    }

    /**
     * Adds an empty line to this item description.
     */
    public fun emptyLine() {
        lines += "&7".colored
    }

    /**
     * Adds any lore that any [ItemBehavior] would want to implement.
     */
    public fun behaviorLore(mainColor: Color) {
        for (component in itemStack.getAllBehaviors()) {
            val lore = component.getLore(itemStack, viewer)
            if (lore != null) {
                lines.add(lore.color(mainColor.textColor))
            }
        }
    }

    public fun toTextComponents(): List<Component> {
        return lines.toList()
    }

    public inline fun <reified B : ItemBehavior> hasBehavior(): Boolean = hasBehavior(B::class)
    public inline fun <reified B : ItemBehavior> getBehavior(): B = getBehavior(B::class)
    public inline fun <reified B : ItemBehavior> getBehaviorOrNull(): B? = getBehaviorOrNull(B::class)

}

/**
 * A descriptor for a [xyz.mastriel.cutapi.item.CustomItem], which contains useful info
 * such as a name, lore, NBT, etc.
 *
 * @param block The builder, used to create the final [ItemDescriptor]
 */
public fun itemDescriptor(block: ItemDescriptorBuilder.() -> Unit): ItemDescriptor =
    ItemDescriptorBuilder().apply(block).build()

/**
 * A default descriptor for a [xyz.mastriel.cutapi.item.CustomItem].
 */
public fun defaultItemDescriptor(): ItemDescriptor =
    ItemDescriptorBuilder().build()

