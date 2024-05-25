package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.CuTPlugin
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.behavior.isRepeatable
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.item.behaviors.itemBehaviorHolder
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.builtin.CustomModelDataAllocated
import xyz.mastriel.cutapi.resources.builtin.Model3D
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.ref
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.EventHandlerList
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
    val itemBehaviors: List<ItemBehavior> = mutableListOf(),
    val onRegister: EventHandlerList<ItemRegisterEvent> = EventHandlerList()
) {

    infix fun with(block: ItemDescriptorBuilder.() -> Unit): ItemDescriptor {
        val other = ItemDescriptorBuilder().apply(block).build()
        return this + other
    }

    operator fun plus(other: ItemDescriptor): ItemDescriptor {
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

data class ItemRegisterEvent(val item: CustomItem<*>)

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

    private val _itemBehaviors = mutableListOf<ItemBehavior>()
    val itemBehaviors: List<ItemBehavior> get() = _itemBehaviors

    val onRegister = EventHandlerList<ItemRegisterEvent>()

    fun behavior(vararg behaviors: ItemBehavior) {
        for (behavior in behaviors) {
            if (this._itemBehaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
            _itemBehaviors.add(behavior)
            with(behavior) {
                modifyDescriptor()
            }
        }
    }

    fun behavior(behaviors: Collection<ItemBehavior>) {
        behavior(*behaviors.toTypedArray())
    }

    fun build(): ItemDescriptor {

        return ItemDescriptor(
            display = display,
            itemBehaviors = _itemBehaviors,
            onRegister = onRegister
        )
    }

    fun display(block: ItemDisplayBuilder.() -> Unit) {
        display = block
    }


    fun noDisplay() {
        display = {}
    }
}

sealed class ItemTexture : CustomModelDataAllocated {

    abstract fun isAvailable(): Boolean

    data class Texture(val texture: ResourceRef<Texture2D>) : ItemTexture() {
        override val customModelData: Int
            get() = texture.getResource()!!.customModelData

        override fun isAvailable() = texture.isAvailable()
    }

    data class Model(val model: ResourceRef<Model3D>) : ItemTexture() {
        override val customModelData: Int
            get() = model.getResource()!!.customModelData

        override fun isAvailable() = model.isAvailable()
    }
}

fun itemTexture(texture: ResourceRef<Texture2D>) = ItemTexture.Texture(texture)

fun itemModel(model: ResourceRef<Model3D>) = ItemTexture.Model(model)

fun itemTexture(plugin: CuTPlugin, path: String) = ItemTexture.Texture(ref(plugin, path))

fun itemModel(plugin: CuTPlugin, path: String) = ItemTexture.Model(ref(plugin, path))

fun itemTexture(stringPath: String) = ItemTexture.Texture(ref(stringPath))

fun itemModel(stringPath: String) = ItemTexture.Model(ref(stringPath))

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
    var name: Component? = null

    var texture: ItemTexture? = null

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

    inline fun <reified B : ItemBehavior> hasBehavior() = hasBehavior(B::class)
    inline fun <reified B : ItemBehavior> getBehavior() = getBehavior(B::class)
    inline fun <reified B : ItemBehavior> getBehaviorOrNull() = getBehaviorOrNull(B::class)

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

