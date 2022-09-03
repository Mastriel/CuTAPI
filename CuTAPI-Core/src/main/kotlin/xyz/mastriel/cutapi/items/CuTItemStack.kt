package xyz.mastriel.cutapi.items

import net.kyori.adventure.text.Component
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.items.components.ComponentHolder
import xyz.mastriel.cutapi.items.components.MaterialComponent
import xyz.mastriel.cutapi.items.components.materialComponentList
import xyz.mastriel.cutapi.nbt.MetapreservingNBTItem
import xyz.mastriel.cutapi.nbt.tags.TagContainer
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.descriptors.DescriptionBuilder
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.utils.nbt
import kotlin.reflect.KClass

class CuTItemStack(val handle: ItemStack) : TagContainer(MetapreservingNBTItem(handle)), ComponentHolder {
    
    var name: Component
        get() = handle.displayName()
        set(value) {
            nameHasChanged = true
            handle.editMeta { meta ->
                meta.displayName(value)
            }
        }

    var customMaterial by customMaterialTag("CuTID", CustomMaterial.Unknown)
    var nameHasChanged by booleanTag("NameHasChanged", false)
    val descriptor get() = customMaterial.descriptor
    var texture = descriptor.texture

    var bukkitMaterial
        get() = handle.type
        set(value) {
            handle.type = value
        }

    val enchantments: MutableMap<Enchantment, Int>
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

        if (!nameHasChanged) {
            val meta = handle.itemMeta
            meta.displayName(descriptor.name)
            handle.itemMeta = meta
        }
    }

    constructor(customMaterial: CustomMaterial, quantity: Int) : this(
        ItemStack(customMaterial.type, quantity).withMaterialId(customMaterial)
    )

    private val componentHandler = materialComponentList(this.customMaterial)

    override fun hasComponent(component: KClass<out MaterialComponent>) = componentHandler.hasComponent(component)
    override fun <T : MaterialComponent> getComponent(component: KClass<T>) = componentHandler.getComponent(component)
    override fun <T : MaterialComponent> getComponentOrNull(component: KClass<T>) = componentHandler.getComponentOrNull(component)
    override fun getAllComponents(): Set<MaterialComponent> = componentHandler.getAllComponents()


    companion object {
        val ItemStack.customId: Identifier
            get() {
                if (this.type.isAir) return unknownID()
                return idOrNull(nbt.getString("CuTID")) ?: unknownID()
            }

        val ItemStack.customIdOrNull: Identifier?
            get() {
                if (this.type.isAir) return null
                return idOrNull(nbt.getString("CuTID"))
            }

        val ItemStack.isCustom: Boolean
            get() {
                if (this.type.isAir) return false
                return customIdOrNull != null
            }

        fun ItemStack.wrap() : CuTItemStack? {
            if (!isCustom || type.isAir) return null
            return CuTItemStack(this)
        }

        private fun ItemStack.withMaterialId(customMaterial: CustomMaterial): ItemStack {
            nbt.setString("CuTID", customMaterial.id.toString())
            return this
        }
    }
}