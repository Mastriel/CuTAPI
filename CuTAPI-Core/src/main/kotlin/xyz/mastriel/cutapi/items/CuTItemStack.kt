package xyz.mastriel.cutapi.items

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTItem
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.nbt.tags.TagContainer
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.descriptors.DescriptionBuilder
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.utils.nbt
import kotlin.reflect.KClass

class CuTItemStack(val handle: ItemStack) : TagContainer(NBTItem(handle, true)) {

    init {
        require(handle.customIdOrNull != null) { "ItemStack not wrappable into a CuTItemStack." }
    }

    constructor(customMaterial: CustomMaterial, quantity: Int) : this(
        ItemStack(
            customMaterial.type,
            quantity
        ).withMaterialId(customMaterial)
    ) {
        for (component in descriptor.components) {
            val componentInstance = component()
            println("Adding component ${componentInstance.id}")
            addComponent(componentInstance)
        }
    }


    var customMaterial by customMaterialTag("CuTID", CustomMaterial.Unknown)
    val descriptor get() = customMaterial.materialDescriptor
    var texture = descriptor.texture

    var bukkitMaterial
        get() = handle.type
        set(value) {
            handle.type = value
        }
    val enchantments
        get() = handle.enchantments

    var name: Component
        get() = handle.displayName()
        set(value) {
            handle.editMeta { it.displayName(value) }
        }

    private val _components by lazy {
        val container = handle.nbt.getOrCreateCompound("CuTAPIComponents") ?: return@lazy mutableSetOf()

        val components = mutableSetOf<ItemComponent>()
        // loop through each component as NBT
        for (id in container.keys.map(::id)) {

            val componentClass = ItemComponent.get(id)
            val componentInstance = ItemComponent.create(componentClass)
            componentInstance.bind(this)

            components += componentInstance
        }

        return@lazy components
    }

    val components get() = _components.toSet()

    internal fun getComponentContainer(component: ItemComponent) : NBTCompound {
        return compound.getOrCreateCompound("CuTAPIComponents")
            .getOrCreateCompound("${component.id}")
    }

    /**
     * Get a component based on its type from this item stack.
     *
     * @param T The type being fetched.
     * @see getComponentOrNull
     * @throws IllegalStateException If the component doesn't exist on this item.
     */
    inline fun <reified T : ItemComponent> getComponent() = getComponent(T::class)

    /**
     * Get a component based on its type from this item stack.
     *
     * @param T The type being fetched.
     * @see getComponentOrNull
     * @throws IllegalStateException If the component doesn't exist on this item.
     */
    fun <T : ItemComponent> getComponent(kClass: KClass<T>) =
        getComponentOrNull(kClass) ?: error("Component ${kClass.qualifiedName} not found!")

    /**
     * Get a component based on its type from this item stack, or null if it doesn't exist.
     *
     * @param T The component type.
     * @see getComponent
     */
    inline fun <reified T : ItemComponent> getComponentOrNull() = getComponentOrNull(T::class)

    /**
     * Get a component based on its type from this item stack, or null if it doesn't exist.
     *
     * @param kClass The component class.
     * @see getComponent
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ItemComponent> getComponentOrNull(kClass: KClass<T>) = components.find { it::class == kClass } as? T

    /**
     * Add a component to this ItemStack.
     *
     * @param T The component type.
     * @param component The component.
     *
     * @return true if the component was added, false otherwise.
     */
    fun <T : ItemComponent> addComponent(component: T): Boolean {
        if (getComponentOrNull(component::class)?.id == component.id) return false

        val wasAdded = _components.add(component)
        if (!wasAdded) return false

        component.bind(this)

        components.forEach { it.onApply(this) }
        return true
    }

    /**
     * Check if this [CustomItemStackOld] has this component.
     *
     * @param T The component type.
     * @return true if this has the component, false otherwise.
     */
    inline fun <reified T : ItemComponent> hasComponent(): Boolean =
        getComponentOrNull<T>() != null

    /**
     * Check if this [CustomItemStackOld] has this component.
     *
     * @param T The component type.
     * @return true if this has the component, false otherwise.
     */
    fun hasComponent(kClass: KClass<out ItemComponent>): Boolean =
        getComponentOrNull(kClass) != null


    val lore: List<Component>
        get() {
            val loreFormatter = descriptor.loreFormatter
            if (loreFormatter != null) {
                val descriptionBuilder = DescriptionBuilder(this)
                val textComponents = descriptionBuilder.apply(loreFormatter).toTextComponents()
                return textComponents
            }
            return emptyList()
        }

    companion object {
        val ItemStack.customId: Identifier
            get() {
                return idOrNull(nbt.getString("CuTID")) ?: unknownID()
            }

        val ItemStack.customIdOrNull: Identifier?
            get() {
                return idOrNull(nbt.getString("CuTID"))
            }

        val ItemStack.isCustom: Boolean
            get() {
                return customId != unknownID()
            }

        private fun ItemStack.withMaterialId(customMaterial: CustomMaterial): ItemStack {
            nbt.setString("CuTID", customMaterial.id.toString())
            return this
        }

        internal fun ItemComponent.bind(itemStack: CuTItemStack) {
            val itemContainer = itemStack.getComponentContainer(this)

            val previousContainer = this.compound

            this.compound = itemContainer
            this.compound.mergeCompound(previousContainer)
        }
    }
}