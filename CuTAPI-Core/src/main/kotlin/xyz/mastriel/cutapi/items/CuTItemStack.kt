package xyz.mastriel.cutapi.items

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTItem
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.items.components.*
import xyz.mastriel.cutapi.nbt.tags.TagContainer
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.descriptors.DescriptionBuilder
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.utils.nbt
import kotlin.reflect.KClass

class CuTItemStack(val handle: ItemStack) : TagContainer(NBTItem(handle, true)),
    ComponentHandler {
    
    var name: Component
        get() = handle.displayName()
        set(value) {
            val meta = handle.itemMeta
            meta.displayName(value)
            handle.itemMeta = meta
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

    fun getLore(viewer: Player): List<Component> {
        val loreFormatter = descriptor.loreFormatter
        if (loreFormatter != null) {
            val descriptionBuilder = DescriptionBuilder(this, viewer)
            return descriptionBuilder.apply(loreFormatter).toTextComponents()
        }
        return emptyList()
    }

    init {
        require(handle.customIdOrNull != null) { "ItemStack not wrappable into a CuTItemStack." }

        name = descriptor.name ?: handle.displayName()
    }

    constructor(customMaterial: CustomMaterial, quantity: Int) : this(
        ItemStack(customMaterial.type, quantity).withMaterialId(customMaterial)
    ) {
        for (component in descriptor.components) {
            val componentInstance = component()
            addComponent(componentInstance)
        }
    }

    private val componentHandler = itemComponentHandler(this)
    
    override fun addComponent(component: ItemComponent) = componentHandler.addComponent(component)
    override fun removeComponent(component: KClass<out ItemComponent>) = componentHandler.removeComponent(component)
    override fun hasComponent(component: KClass<out ItemComponent>) = componentHandler.hasComponent(component)
    override fun <T: ItemComponent> getComponent(component: KClass<T>) = componentHandler.getComponent(component)
    override fun <T: ItemComponent> getComponentOrNull(component: KClass<T>) = componentHandler.getComponentOrNull(component)
    override fun getAllComponents() : Set<ItemComponent> = componentHandler.getAllComponents()


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
    }
}