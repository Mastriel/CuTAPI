package xyz.mastriel.cutapi.items

import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.nbt.edit
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.descriptors.DescriptionBuilder
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.utils.nbt
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.system.measureNanoTime


class CustomItemStack(val customMaterial: CustomMaterial, var quantity: Int) {

    val descriptor get() = customMaterial.materialDescriptor

    var name = descriptor.name
    var texture = descriptor.texture
    var bukkitMaterial = customMaterial.type

    var damage = 0

    var enchantments = mutableMapOf<Enchantment, Int>()

    private val _components = mutableSetOf<ItemComponent>()
    val components get() = _components.toSet()


    /**
     * Get a component based on its type from this item stack.
     *
     * @param T The type being fetched.
     * @see getComponentOrNull
     * @throws IllegalStateException If the component doesn't exist on this item.
     */
    inline fun <reified T: ItemComponent> getComponent() = getComponent(T::class)

    /**
     * Get a component based on its type from this item stack.
     *
     * @param T The type being fetched.
     * @see getComponentOrNull
     * @throws IllegalStateException If the component doesn't exist on this item.
     */
    fun <T: ItemComponent> getComponent(kClass: KClass<T>) =
        getComponentOrNull(kClass) ?: error("Component ${kClass.qualifiedName} not found!")

    /**
     * Get a component based on its type from this item stack, or null if it doesn't exist.
     *
     * @param T The component type.
     * @see getComponent
     */
    inline fun <reified T: ItemComponent> getComponentOrNull() = getComponentOrNull(T::class)

    /**
     * Get a component based on its type from this item stack, or null if it doesn't exist.
     *
     * @param kClass The component class.
     * @see getComponent
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: ItemComponent> getComponentOrNull(kClass: KClass<T>) = components.find { it::class == kClass } as? T

    /**
     * Add a component to this ItemStack.
     *
     * @param T The component type.
     * @param component The component.
     *
     * @return true if the component was added, false otherwise.
     */
    fun <T: ItemComponent> addComponent(component: T) : Boolean {
        if (getComponentOrNull(component::class)?.id == component.id) return false
        val wasAdded = _components.add(component)
        if (!wasAdded) return false
        components.forEach { it.onApply(this) }
        return true
    }

    /**
     * Check if this [CustomItemStack] has this component.
     *
     * @param T The component type.
     * @return true if this has the component, false otherwise.
     */
    inline fun <reified T : ItemComponent> hasComponent() : Boolean =
        getComponentOrNull<T>() != null



    /**
     * Converts this [CustomItemStack] into a normal Bukkit [ItemStack]. This will also invoke
     * [CustomMaterial.onCreate].
     *
     * @return A Bukkit ItemStack using all the data from this.
     */
    fun toBukkitItemStack() : ItemStack {

        val bukkitItemStack = ItemStack(bukkitMaterial, quantity)
        println("NBT: " + measureNanoTime {
            setNBT(bukkitItemStack)
        })

        customMaterial.onCreate(this)

        bukkitItemStack.editMeta {
            it.displayName(name)
        }
        println("Lore: " + measureNanoTime {
            setLore(bukkitItemStack)
        })
        bukkitItemStack.addEnchantments(enchantments)


        return bukkitItemStack
    }

    private fun setNBT(bukkitItemStack: ItemStack) {
        val nbt = bukkitItemStack.nbt

        // merge the default described nbt
        nbt.mergeCompound(descriptor.container)
        nbt.edit {
            string("CuTAPIID", customMaterial.id.toString())

            compound("CuTAPIComponents") {
                components.forEach {
                    compound("${it.id}", it.container)
                }
            }
        }
        nbt.mergeNBT(bukkitItemStack)
    }

    private fun setLore(bukkitItemStack: ItemStack) {
        val loreFormatter = descriptor.loreFormatter
        if (loreFormatter != null) {
            val descriptionBuilder = DescriptionBuilder(this)
            val textComponents = descriptionBuilder.apply(loreFormatter).toTextComponents()
            bukkitItemStack.lore(textComponents)
        }
    }

    companion object {
        /**
         * Converts a [itemStack] into a [CustomItemStack]. This may return null due to an [ItemStack] not
         * having the NBT fields of a [CustomItemStack], so it should be used with caution.
         *
         * @return A [CustomItemStack] containing *most* of the data from a Bukkit [ItemStack]. Some notable
         * data is discarded, such as the lore, which is automatically regenerated when the [CustomItemStack]
         * is transformed back into a [ItemStack]
         */
        fun fromVanillaOrNull(itemStack: ItemStack) : CustomItemStack? {
            val identifier = itemStack.cuTID
            val customMaterial = CustomMaterial.getOrNull(identifier) ?: return null
            val customItemStack = CustomItemStack(customMaterial, itemStack.amount)

            setComponents(itemStack, customItemStack)

            customItemStack.bukkitMaterial = itemStack.type
            customItemStack.name = itemStack.itemMeta.displayName()
            customItemStack.enchantments = itemStack.enchantments.toMutableMap()

            return customItemStack
        }

        fun fromVanilla(itemStack: ItemStack) : CustomItemStack {
            return fromVanillaOrNull(itemStack) ?: error("ItemStack is not a CustomItemStack.")
        }


        private fun setComponents(itemStack: ItemStack, customItemStack: CustomItemStack) {
            val container = itemStack.nbt.getCompound("CuTAPIComponents") ?: return

            // loop through each component as NBT
            for (id in container.keys.map(::id)) {

                val compound = container.getCompound(id.toString())
                val componentClass = ItemComponent.get(id)

                val componentInstance = componentClass.createInstance()

                customItemStack.addComponent(componentInstance)
            }
        }

        val ItemStack.cuTID : Identifier get() {
            return idOrNull(nbt.getString("CuTAPIID")) ?: unknownID()
        }
    }

}