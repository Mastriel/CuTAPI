package xyz.mastriel.cutapi.registry.descriptors

import de.tr7zw.changeme.nbtapi.NBTContainer
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.nbt.HasNBT
import xyz.mastriel.cutapi.nbt.NBTBuilder
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
     * The default NBT values applied to this custom material.
     * */
    open val container: NBTContainer? = null,
    /**
     * The texture that this custom material should use, by default.
     * */
    open val texture: Texture? = null,
    open val loreFormatter: (DescriptionBuilder.() -> Unit)? = null,
    open val components: Set<ItemComponent> = setOf()
)

/**
 * A builder for the [MaterialDescriptor], containing some useful functions to make creating
 * resources much easier.
 *
 * @see MaterialDescriptor
 */
open class MaterialDescriptorBuilder internal constructor() : HasNBT {
    var name : Component? = null
    override val nbtContainer = NBTContainer()
    var texture : Texture? = null
    private var formatter : (DescriptionBuilder.() -> Unit)? = {
        emptyLine()
        itemComponents(Color.Blue)
    }

    val components = mutableSetOf<ItemComponent>()

    fun component(component: ItemComponent) {
        if (components.find { it.id == component.id } != null) return
        components += component
    }

    fun build() : MaterialDescriptor {
        return MaterialDescriptor(
            name = name,
            loreFormatter = formatter,
            container = nbtContainer,
            texture = texture,
            components = components
        )
    }

    /**
     * A method to modify the default NBT of the custom material.
     *
     * @param block The NBTBuilder used.
     */
    fun defaultNBT(block: NBTBuilder.() -> Unit) {
        val newContainer = NBTBuilder().apply(block).build()
        nbtContainer.mergeCompound(newContainer)
    }

    /**
     * A method to add your own custom NBT data to the custom material. This
     * is very similar to [defaultNBT], but puts all data into the "CuTAPIData" NBT
     * compound, keeping it separate from vanilla values.
     *
     * @param block The NBTBuilder used.
     */
    fun data(block: NBTBuilder.() -> Unit) {
        val compound = nbtContainer.addCompound("CuTAPIData")
        compound.mergeCompound(NBTBuilder().apply(block).build())
    }

    fun description(block: DescriptionBuilder.() -> Unit) {
        formatter = block
    }

    fun noDescription() {
        formatter = {}
    }
}

/**
 * A class for creating dynamic descriptions for [CustomItemStack]s.
 * This is stored in the [MaterialDescriptor] and re-runs every time
 * the item is turned into a Bukkit [ItemStack].
 */
class DescriptionBuilder(val itemStack: CustomItemStack) {

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
     * Adds any lore that any [ItemComponent] would want to implement.
     */
    fun itemComponents(color: Color) {
        for (itemComponent in itemStack.components.distinct()) {
            val lore = itemComponent.lore
            if (lore != null) {
                lines.add(lore.color(color.textColor))
            }
        }
    }

    fun toTextComponents() : List<Component> {
        return lines.toList()
    }
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
 * A default descriptor for a [xyz.mastriel.cutapi.items.CustomMaterial]. No values are set in this, and
 * and without a custom [xyz.mastriel.cutapi.items.CustomMaterial.onCreate] block, this will create a
 * vanilla Minecraft item with only the [xyz.mastriel.cutapi.registry.Identifier] NBT given to all custom items
 * to identify them.
 */
fun defaultMaterialDescriptor() =
    MaterialDescriptorBuilder().build()